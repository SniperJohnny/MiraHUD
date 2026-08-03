package io.sniperjohnny.github.mirahud.client.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.config.MasterConfigManager;
import io.sniperjohnny.github.mirahud.client.overlay.config.FilePathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plays local video files (MP4, MKV, etc.) via external FFmpeg processes.
 * <p>
 * Uses a raw RGBA pipe from FFmpeg.  Frames are uploaded to a
 * {@link DynamicTexture} using the modern texture API:
 * {@link DynamicTexture#setPixels(NativeImage)} + {@link DynamicTexture#upload()}.
 * No raw GL calls, no reflection, no accessor mixins.
 * <p>
 * In 1.21.11 {@link DynamicTexture#upload()} always re-uploads the NativeImage
 * backing memory to the GPU (CommandEncoder.writeToTexture → glTexSubImage2D
 * reading {@code NativeImage.getPointer()}), so in-place writes to the shared
 * NativeImage are picked up every frame. Calling {@code setPixels()} per frame
 * would instead FREE the reused NativeImage (it closes the previous image).
 * <p>
 * <b>A/V sync:</b> the video FFmpeg process decodes at max speed (no {@code -re}
 * — {@code -re} throttles on Windows pipes and lets the video race ahead of the
 * audio). The audio stream is the master clock (real-time). The frame reader
 * holds each decoded frame until the audio playback position reaches that
 * frame's presentation timestamp, obtained in-band from FFmpeg's
 * {@code showinfo} filter on stderr. If timestamps are unavailable, or the
 * video has no audio, plain FPS pacing is used as a fallback.
 */
public class VideoTextureManager implements MediaProvider {
    private final Identifier textureId;
    private final String overlayId;

    private DynamicTexture dynTexture;
    private NativeImage nativeImage;
    private String currentSource;
    /** Resolved path of the currently-loaded source (needed for seek/restart after cleanup). */
    private String lastResolvedSource;
    /** Seek offset to apply on the next pipeline start (0 = from beginning). */
    private volatile double seekPosition;
    private int originalWidth;
    private int originalHeight;
    private int frameSize;

    private Process ffmpegVideoProcess;
    private Process ffmpegAudioProcess;
    private AudioStreamer audioStreamer;
    private Thread frameReaderThread;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final Object frameLock = new Object();
    private volatile boolean frameReady = false;
    private volatile float volume = 1.0f;
    /** True when the video played to its end (clean EOF after frames were read). */
    private volatile boolean finishedNaturally = false;
    /** When true, auto-restart from beginning after natural EOF. Set by the render loop from OverlayConfig. */
    private volatile boolean loop = false;
    /** Video frame rate (fps), probed via ffprobe. 0 if unknown. */
    private volatile double videoFps = 0;

    /** Direct byte buffer filled by the reader thread. */
    private ByteBuffer rgbaBuffer;
    /** Working buffer for reading from the stream. */
    private byte[] readBuffer;
    /** Resolution of the currently-allocated GPU texture (0 = none). Used to
     *  reuse the texture across same-source restarts (seek/loop). */
    private int textureWidth = 0;
    private int textureHeight = 0;

    // ---- Frame-timestamp capture (FFmpeg showinfo on stderr) ----

    /** Matches showinfo's frame counter: "n:   0" (0-based, output frame order). */
    private static final Pattern FRAME_N_PATTERN = Pattern.compile("n:\\s*(\\d+)");
    /** Matches showinfo's presentation time: "pts_time:12.345". */
    private static final Pattern PTS_PATTERN = Pattern.compile("pts_time:(-?[0-9]+(?:\\.[0-9]+)?)");

    private record PtsEntry(int frameIndex, double ptsTime) {}

    /** Per-frame presentation timestamps in output order (fed by the stderr thread). */
    private volatile ConcurrentLinkedQueue<PtsEntry> ptsQueue = new ConcurrentLinkedQueue<>();
    /** False once PTS capture proves unavailable → permanent frame-count pacing. */
    private volatile boolean usePts = true;
    /** Consecutive pts-miss counter (only touched by the frame reader thread). */
    private int ptsMissCount = 0;
    /** Give up on per-frame PTS after this many consecutive misses (~0.6s). */
    private static final int PTS_MISS_LIMIT = 6;
    /** How long {@link #pollPts} waits for the stderr thread to deliver a frame's pts. */
    private static final long PTS_POLL_NS = 100_000_000L;
    /** pts of the first output frame — subtracted from every pts so the video clock
     *  starts at 0 (matching the audio clock when it becomes audible), regardless of
     *  the container's start offset or demuxer flags. Only touched by the stderr thread. */
    private double firstPts = Double.NaN;
    /** Wall-clock anchor (monotonic) for the first decoded frame — drives the fallback
     *  pacing while the audio line is not yet audible / for silent videos. */
    private long playbackStartNanos = 0;

    // ==================== Constructor ====================

    public VideoTextureManager(String overlayId) {
        this.overlayId = overlayId;
        this.textureId = Identifier.fromNamespaceAndPath(
                MiraHUD.MOD_ID, "video_overlay_" + overlayId.replace("-", "_")
        );
    }

    // ==================== MediaProvider ====================

    @Override public Identifier getTextureId() { return textureId; }
    @Override public boolean hasTexture() { return dynTexture != null; }
    @Override public String getCurrentSource() { return currentSource == null ? "" : currentSource; }
    @Override public int getOriginalWidth() { return originalWidth; }
    @Override public int getOriginalHeight() { return originalHeight; }
    @Override public boolean isVideo() { return true; }
    @Override public boolean isPlaying() { return playing.get(); }

    /**
     * @return true while the FFmpeg pipeline is still alive (not crashed/EOF).
     *         A video that finished playing naturally also counts as "alive" so
     *         the render loop does NOT restart it — only a crash (EOF with zero
     *         frames read) triggers the self-heal restart.
     */
    @Override
    public boolean isAlive() {
        // When loop is on and the video finished naturally, report "not alive"
        // so the render loop auto-restarts it immediately.
        if (finishedNaturally && loop) return false;
        return running.get() || finishedNaturally;
    }

    /** Set the loop flag (called from the render loop when config changes). */
    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    @Override
    public void seek(double seconds) {
        double target = Math.max(0, seconds);
        String rawSource = currentSource;
        String resolved = lastResolvedSource;
        if (resolved == null) return;
        MiraHUD.LOGGER.info("Seeking to {}s in {}", target, overlayId);
        seekPosition = target;
        // Soft teardown: the FFmpeg processes/threads/audio die, but the GPU
        // texture, native image and direct buffers survive — startFfmpeg reuses
        // them because the source (and thus resolution) is unchanged, so the
        // restart is seamless instead of flashing the overlay.
        teardown(true);
        currentSource = rawSource;
        lastResolvedSource = resolved;
        try {
            startFfmpeg(resolved);
        } catch (IOException e) {
            MiraHUD.LOGGER.error("Failed to restart after seek: {}", e.getMessage());
        }
    }

    @Override
    public void restart() {
        seek(0);
    }

    @Override
    public double getPlaybackPositionSeconds() {
        if (audioStreamer != null && audioStreamer.isActive()) {
            return audioStreamer.getPlaybackPositionSeconds() + seekPosition;
        }
        // Fallback: wall-clock time since playback started + seek offset
        if (playbackStartNanos > 0) {
            return (System.nanoTime() - playbackStartNanos) / 1_000_000_000.0 + seekPosition;
        }
        return seekPosition;
    }

    @Override
    public boolean updateSource(String source) {
        if (source == null || source.isBlank()) { cleanup(); return true; }
        // Identity check against the RAW config path so the render loop
        // never sees a resolved-path mismatch and recreates this provider.
        if (source.equals(currentSource) && running.get()) return true;

        String resolved = FilePathUtil.resolve(source);

        if (!Files.exists(Path.of(resolved))) {
            MiraHUD.LOGGER.warn("Video file does not exist after resolution: {}", resolved);
            return false;
        }
        if (!ExternalToolManager.isFfmpegAvailable()) {
            if (!ExternalToolManager.isDownloadInProgress()) {
                ExternalToolManager.showMissingFfmpegToast();
                MiraHUD.LOGGER.error("No usable FFmpeg. A portable build is being downloaded, or install FFmpeg manually.");
            }
            return false;
        }
        if (!ExternalToolManager.isFfprobeAvailable()) {
            if (!ExternalToolManager.isDownloadInProgress()) {
                ExternalToolManager.showMissingFfprobeToast();
                MiraHUD.LOGGER.error("No usable FFprobe (it ships with the portable FFmpeg download).");
            }
            return false;
        }

        cleanup();
        seekPosition = 0;
        currentSource = source;
        lastResolvedSource = resolved;

        try {
            startFfmpeg(resolved);
            return true;
        } catch (IOException e) {
            MiraHUD.LOGGER.error("Failed to start FFmpeg: {}", e.getMessage());
            return false;
        }
    }

    // ==================== FFmpeg ====================

    private void startFfmpeg(String source) throws IOException {
        if (!probeDimensions(source)) {
            throw new IOException("Failed to probe video dimensions for " + source);
        }

        int maxHeight = MasterConfigManager.getConfig().maxVideoHeight;
        boolean needsScale = maxHeight > 0 && originalHeight > maxHeight;
        if (needsScale) {
            float scale = (float) maxHeight / originalHeight;
            originalWidth  = Math.max(2, Math.round(originalWidth  * scale)) & ~1;
            originalHeight = Math.max(2, Math.round(originalHeight * scale)) & ~1;
            MiraHUD.LOGGER.info("Scaling video to {}x{} for overlay playback", originalWidth, originalHeight);
        }

        frameSize = originalWidth * originalHeight * 4;

        final int w = originalWidth, h = originalHeight;
        // Reuse the existing texture + native image when the resolution is
        // unchanged (seek/restart/loop) so restarts are seamless and don't
        // churn the GPU. When called on the render thread (the normal case —
        // seek keybinds, the render loop, config screens all run there) the
        // texture is (re)created synchronously; otherwise it is deferred.
        Runnable createTexture = () -> {
            if (dynTexture != null && nativeImage != null
                    && textureWidth == w && textureHeight == h) {
                return; // same resolution — reuse in place
            }
            if (dynTexture != null) {
                Minecraft.getInstance().getTextureManager().release(textureId);
                dynTexture.close();
            }
            if (nativeImage != null) {
                nativeImage.close();
            }
            dynTexture = new DynamicTexture(textureId::toString, w, h, false);
            Minecraft.getInstance().getTextureManager().register(textureId, dynTexture);

            // Create reusable NativeImage for per-frame uploads
            nativeImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            dynTexture.setPixels(nativeImage);
            // Upload initial (empty) pixels to initialize the GPU texture
            dynTexture.upload();
            textureWidth = w;
            textureHeight = h;
        };
        if (Minecraft.getInstance().isSameThread()) {
            createTexture.run();
        } else {
            Minecraft.getInstance().execute(createTexture);
        }

        // Reuse the direct buffers when the frame size is unchanged (restarts).
        if (rgbaBuffer == null || rgbaBuffer.capacity() != frameSize) {
            rgbaBuffer = ByteBuffer.allocateDirect(frameSize);
            readBuffer = new byte[frameSize];
        }

        // --- Video process: raw RGBA ---
        List<String> vCmd = new ArrayList<>();
        // loglevel info so the showinfo filter can report per-frame timestamps
        // (the stderr thread below filters out everything except timestamps).
        vCmd.add(ExternalToolManager.ffmpegCommand()); vCmd.add("-loglevel"); vCmd.add("info");
        // NOTE: -re is intentionally NOT used on the video process. -re paces the
        // demuxer to wall clock, but (a) on Windows it throttles large rawvideo
        // frames to a few fps through the pipe, and (b) it bursts through the
        // first seconds of content at startup, letting the video race 2-3s ahead
        // of the audio. The Java side now paces each frame to the audio clock
        // using the frame's pts, so FFmpeg can decode at max speed safely.
        if (MasterConfigManager.getConfig().ffmpegHardwareAccel) {
            // Opt-in only: -hwaccel auto is known to crash on some Windows
            // GPU/driver combos, so software decoding is the default.
            vCmd.add("-hwaccel"); vCmd.add("auto");
        }
        // NOTE: only +discardcorrupt is used. +nobuffer is intentionally NOT used:
        // with it, the demuxer skips the container's start-time normalization and
        // reports the FIRST video frame at pts 2.0+ for MKVs (verified on a real
        // episode file), which made the video wait ~3s for the audio clock to
        // "catch up" at startup. parseShowInfo also normalizes pts to the first
        // frame, so sync is immune to any remaining container offset either way.
        vCmd.add("-fflags"); vCmd.add("+discardcorrupt");
        if (seekPosition > 0) {
            vCmd.add("-ss"); vCmd.add(String.format(java.util.Locale.ROOT, "%.3f", seekPosition));
        }
        vCmd.add("-i"); vCmd.add(source);
        vCmd.add("-an");
        vCmd.add("-pix_fmt"); vCmd.add("rgba");
        if (needsScale) {
            vCmd.add("-vf"); vCmd.add("scale=" + originalWidth + ":" + originalHeight + ",showinfo");
        } else {
            vCmd.add("-vf"); vCmd.add("showinfo");
        }
        vCmd.add("-f"); vCmd.add("rawvideo");
        vCmd.add("pipe:1");

        ProcessBuilder vPb = new ProcessBuilder(vCmd);
        // stderr is left as a pipe: it carries the showinfo frame timestamps,
        // which the parse thread below turns into the A/V sync clock. Real
        // FFmpeg errors on it are still forwarded to the log.
        ffmpegVideoProcess = ExternalToolManager.startWithRetry(vPb);

        // Parse stderr for per-frame presentation timestamps (showinfo lines).
        final ConcurrentLinkedQueue<PtsEntry> queue = new ConcurrentLinkedQueue<>();
        this.ptsQueue = queue;
        InputStream stderr = ffmpegVideoProcess.getErrorStream();
        Thread ptsThread = new Thread(() -> parseShowInfo(stderr, queue), "Video-Pts-" + overlayId);
        ptsThread.setDaemon(true);
        ptsThread.start();

        // --- Audio process ---
        List<String> aCmd = new ArrayList<>();
        aCmd.add(ExternalToolManager.ffmpegCommand()); aCmd.add("-loglevel"); aCmd.add("quiet");
        aCmd.add("-re");
        if (seekPosition > 0) {
            aCmd.add("-ss"); aCmd.add(String.format(java.util.Locale.ROOT, "%.3f", seekPosition));
        }
        aCmd.add("-i"); aCmd.add(source);
        aCmd.add("-vn"); aCmd.add("-f"); aCmd.add("s16le");
        aCmd.add("-ar"); aCmd.add("44100"); aCmd.add("-ac"); aCmd.add("2");
        aCmd.add("pipe:1");

        ProcessBuilder aPb = new ProcessBuilder(aCmd);
        aPb.redirectError(ProcessBuilder.Redirect.INHERIT);
        ffmpegAudioProcess = ExternalToolManager.startWithRetry(aPb);

        running.set(true);
        playing.set(true);

        InputStream audioStream = new BufferedInputStream(ffmpegAudioProcess.getInputStream(), 32768);
        audioStreamer = new AudioStreamer(audioStream);
        audioStreamer.setVolume(volume);
        // Audio start is deferred until the first video frame is read,
        // so the audio doesn't race ahead of the video on startup.

        InputStream videoStream = new BufferedInputStream(ffmpegVideoProcess.getInputStream(), 65536);
        frameReaderThread = new Thread(() -> readFrames(videoStream), "Video-Frames-" + overlayId);
        frameReaderThread.setDaemon(true);
        frameReaderThread.start();
    }

    /**
     * Probe the source's video dimensions with ffprobe.
     * @return true if valid dimensions were obtained; false if probing failed
     *         or timed out. On failure the video is NOT started — a guessed
     *         frame size would misalign every frame read from the pipe.
     */
    private boolean probeDimensions(String source) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(ExternalToolManager.ffprobeCommand()); cmd.add("-v"); cmd.add("error");
            cmd.add("-select_streams"); cmd.add("v:0");
            cmd.add("-show_entries"); cmd.add("stream=width,height,r_frame_rate");
            cmd.add("-of"); cmd.add("csv=p=0");
            cmd.add(source);
            Process p = ExternalToolManager.startWithRetry(new ProcessBuilder(cmd));
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                MiraHUD.LOGGER.warn("ffprobe timed out for {}", source);
                return false;
            }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (!out.isEmpty()) {
                String[] parts = out.split(",");
                if (parts.length >= 2) {
                    originalWidth  = Integer.parseInt(parts[0].trim());
                    originalHeight = Integer.parseInt(parts[1].trim());
                    if (parts.length >= 3) {
                        videoFps = parseFrameRate(parts[2].trim());
                    }
                    if (videoFps <= 0) {
                        videoFps = 24;
                        MiraHUD.LOGGER.warn("Could not probe video FPS, defaulting to 24");
                    } else {
                        MiraHUD.LOGGER.info("Video FPS: {} (raw: {})", videoFps, parts.length >= 3 ? parts[2].trim() : "N/A");
                    }
                    return originalWidth > 0 && originalHeight > 0;
                }
            }
            return false;
        } catch (Exception e) {
            MiraHUD.LOGGER.warn("Failed to probe video dimensions for {}", source, e);
            return false;
        }
    }

    // ==================== Frame reader (background thread) ====================

    /**
     * Reads raw RGBA frames from the FFmpeg pipe and publishes them to the
     * shared buffer for the render thread to upload.
     * <p>
     * Each frame is held back (the pipe write blocks FFmpeg in the meantime)
     * until the audio clock reaches the frame's presentation timestamp, so the
     * video can never display ahead of the audio. Silent videos / unavailable
     * timestamps fall back to plain FPS pacing.
     */
    private void readFrames(InputStream videoStream) {
        int framesRead = 0;
        try (DataInputStream dataIn = new DataInputStream(videoStream)) {
            while (running.get()) {
                ByteBuffer localRgba = rgbaBuffer;
                byte[] localRead = readBuffer;
                int localFrameSize = frameSize;
                if (localRgba == null || localRead == null || localFrameSize <= 0) break;

                // Blocking read of one frame. FFmpeg decodes at max speed
                // (no -re) and the pipe handshake back-pressures it while we
                // pace below.
                dataIn.readFully(localRead, 0, localFrameSize);
                framesRead++;

                if (framesRead == 1) {
                    playbackStartNanos = System.nanoTime();
                    if (audioStreamer != null) audioStreamer.start();
                    MiraHUD.LOGGER.info("Video frame 1 ready, audio started");
                }

                // Hold the frame until the audio clock reaches its pts, so the
                // video can never race ahead of the audio (fixes 2-3s audio lag).
                paceFrame(framesRead);
                if (!running.get()) break;

                synchronized (frameLock) {
                    if (rgbaBuffer == null) break;
                    rgbaBuffer.clear();
                    rgbaBuffer.put(localRead, 0, localFrameSize);
                    rgbaBuffer.flip();
                    frameReady = true;
                }

                if (framesRead % 100 == 0) {
                    MiraHUD.LOGGER.info("Video frame {} read successfully", framesRead);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                if (framesRead > 0) {
                    // Clean end-of-stream after frames flowed — video played to the end.
                    finishedNaturally = true;
                    MiraHUD.LOGGER.info("Video playback ended after {} frames", framesRead);
                } else {
                    // EOF with zero frames = FFmpeg crashed before decoding anything.
                    MiraHUD.LOGGER.warn("Video frame read error after {} frames ({}): {}",
                            framesRead, e.getClass().getSimpleName(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    /**
     * Pace the current frame to the master clock (audio when audible, wall clock
     * otherwise), so the video never displays ahead of the audio.
     * <p>
     * The frame's target time is its true presentation timestamp when PTS capture
     * works, otherwise frame-count (frameIndex / fps). Both target clocks are
     * self-correcting: if the video decodes slower than real time the target is
     * already in the past and the frame is published immediately (the video
     * naturally lags instead of racing). No cumulative drift is possible — the
     * previous FPS fallback slept 1/fps on top of the frame-read time, which
     * cut a 60fps file's effective rate to ~33fps and let the audio outrun the
     * video.
     *
     * @param framesRead 1-based index of the frame just decoded
     */
    private void paceFrame(int framesRead) {
        if (videoFps <= 0) return;
        int frameIndex = framesRead - 1;

        boolean audioAudible = audioStreamer != null && audioStreamer.isActive() && !audioStreamer.isEof();

        // Default target: frame-count pacing, evaluated against whichever clock
        // is live below. Replaced by the true per-frame pts when available.
        double target = frameIndex / videoFps;

        if (audioAudible && usePts) {
            double pts = pollPts(frameIndex);
            if (pts >= 0) {
                ptsMissCount = 0;
                target = pts;
            } else if (++ptsMissCount >= PTS_MISS_LIMIT) {
                // Only give up after sustained failure, so a slow stderr delivery
                // at startup doesn't permanently disable sync.
                usePts = false;
                MiraHUD.LOGGER.warn("Frame timestamps unavailable for overlay {}; using frame-count pacing.", overlayId);
            }
            // Transient miss: keep the frame-count target for this frame only.
        }

        double clockNow = audioAudible
                ? audioStreamer.getPlaybackPositionSeconds()
                : (System.nanoTime() - playbackStartNanos) / 1_000_000_000.0;

        double ahead = target - clockNow;
        if (ahead > 0.005) {
            sleepWhileRunning((long) ((ahead - 0.005) * 1_000_000_000.0));
        }
    }

    /**
     * Pop the presentation timestamp for a given output frame index (0-based,
     * matching showinfo's {@code n:} counter), waiting briefly for the stderr
     * thread to deliver it.
     * @return the frame's normalized pts in seconds, or -1 if unavailable/timed out
     */
    private double pollPts(int frameIndex) {
        long deadline = System.nanoTime() + PTS_POLL_NS;
        while (running.get() && System.nanoTime() < deadline) {
            PtsEntry entry = ptsQueue.peek();
            if (entry != null) {
                if (entry.frameIndex < frameIndex) {
                    ptsQueue.poll(); // stale entry — drop and continue
                    continue;
                }
                if (entry.frameIndex == frameIndex) {
                    ptsQueue.poll();
                    return entry.ptsTime;
                }
                return -1; // out of order — treat as unavailable
            }
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                break;
            }
        }
        return -1;
    }

    /**
     * Reads FFmpeg's stderr: extracts per-frame {@code showinfo} timestamps
     * into {@code queue}, forwards real errors to the log, and swallows the
     * rest (the info-level startup chatter).
     */
    private void parseShowInfo(InputStream stderrStream, ConcurrentLinkedQueue<PtsEntry> queue) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stderrStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher nMatcher = FRAME_N_PATTERN.matcher(line);
                Matcher ptsMatcher = PTS_PATTERN.matcher(line);
                if (nMatcher.find() && ptsMatcher.find()) {
                    try {
                        int n = Integer.parseInt(nMatcher.group(1));
                        double pts = Double.parseDouble(ptsMatcher.group(1));
                        // Normalize to the first frame's pts so the video clock
                        // always starts at 0, matching the audio clock when it
                        // becomes audible. Handles MKVs whose container start
                        // time is non-zero (e.g. demuxer offset handling).
                        if (Double.isNaN(firstPts)) firstPts = pts;
                        queue.add(new PtsEntry(n, pts - firstPts));
                    } catch (NumberFormatException ignored) {
                        // malformed line — skip
                    }
                } else if (isFfmpegErrorLine(line)) {
                    MiraHUD.LOGGER.error("[ffmpeg] {}", line);
                } else {
                    MiraHUD.LOGGER.debug("[ffmpeg] {}", line);
                }
            }
        } catch (IOException e) {
            // stderr closed — the FFmpeg process was stopped/destroyed; expected.
        }
    }

    /**
     * Broad match for FFmpeg error messages, so real failures stay visible in
     * the log even though the video process stderr is piped (not INHERIT) for
     * pts parsing. Showinfo timestamp lines are handled before this is called.
     */
    private static boolean isFfmpegErrorLine(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("error")
                || lower.contains("invalid")
                || lower.contains("failed")
                || lower.contains("no such")
                || lower.contains("not found")
                || lower.contains("unsupported")
                || lower.contains("could not")
                || lower.contains("does not contain any stream");
    }

    /** Sleep for {@code nanos} in short interruptible slices, honoring shutdown. */
    private void sleepWhileRunning(long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (running.get()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try {
                Thread.sleep(Math.min(remaining / 1_000_000L, 10L));
            } catch (InterruptedException e) {
                // Round 13 lesson: do NOT re-assert the interrupt flag here —
                // the outer while (running.get()) handles shutdown.
                break;
            }
        }
    }

    // ==================== Tick (render thread) ====================

    @Override
    public void tick() {
        if (!running.get() || !playing.get()) return;
        if (dynTexture == null || nativeImage == null) return;
        if (frameSize <= 0) return;

        synchronized (frameLock) {
            if (!frameReady) return;
            frameReady = false;

            // Write RGBA data directly into the NativeImage's native memory
            ByteBuffer localRgba = rgbaBuffer;
            if (localRgba == null) return;
            ByteBuffer nativeBuf = MemoryUtil.memByteBuffer(nativeImage.getPointer(), frameSize);
            nativeBuf.clear();
            nativeBuf.put(localRgba);
            nativeBuf.flip();
        }

        // In 1.21.11 upload() always re-uploads the NativeImage's memory to the
        // GPU, so the pixels written above are visible immediately.
        dynTexture.upload();
    }

    @Override
    public void playPause(boolean play) {
        playing.set(play);
        if (audioStreamer != null) audioStreamer.setPaused(!play);
    }

    @Override
    public void setVolume(float vol) {
        float clamped = Math.max(0f, Math.min(1f, vol));
        // Skip the audio-API round-trip when nothing changed — the render loop
        // calls setVolume every frame, so this avoids per-frame control lookups.
        if (clamped == this.volume) return;
        this.volume = clamped;
        if (audioStreamer != null) audioStreamer.setVolume(this.volume);
    }

    // ==================== Cleanup ====================

    @Override
    public void cleanup() {
        teardown(false);
    }

    /**
     * Tears down the playback pipeline.
     * <p>
     * {@code preserveTexture == true} keeps the GPU texture, native image and
     * direct buffers (plus the source/dimension bookkeeping) alive so a
     * same-source restart — seek, restart or loop — can reuse them seamlessly.
     * It is only used by {@link #seek}, which always restarts the same source,
     * so the resolution is guaranteed unchanged. PTS/clock state is always
     * reset because every pipeline start gets fresh timestamps.
     */
    private void teardown(boolean preserveTexture) {
        running.set(false);
        playing.set(false);

        Thread oldReader = frameReaderThread;
        frameReaderThread = null;
        if (oldReader != null) {
            oldReader.interrupt();
        }
        if (audioStreamer != null) {
            audioStreamer.stop();
            audioStreamer = null;
        }

        for (Process p : new Process[]{ffmpegVideoProcess, ffmpegAudioProcess}) {
            if (p != null) {
                p.destroyForcibly();
                try { p.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            }
        }
        ffmpegVideoProcess = null;
        ffmpegAudioProcess = null;

        // Make sure the old reader thread is fully unwound before a new
        // pipeline (which reuses the same buffers in preserve mode) starts.
        // Destroying the processes unblocks its blocking read; joining then
        // prevents it from racing the new reader on the shared rgbaBuffer/
        // readBuffer, or clobbering the new pipeline's running flag in its
        // finally block.
        if (oldReader != null) {
            try { oldReader.join(2000); } catch (InterruptedException ignored) {}
        }

        frameReady = false;
        finishedNaturally = false;

        if (!preserveTexture) {
            // Close NativeImage before DynamicTexture
            if (nativeImage != null) {
                nativeImage.close();
                nativeImage = null;
            }
            if (dynTexture != null) {
                DynamicTexture tex = dynTexture;
                dynTexture = null;
                // Release synchronously on the render thread so the teardown
                // is fully ordered BEFORE startFfmpeg (re)creates a texture
                // under the same id — a deferred release could otherwise free
                // the replacement texture. Defer only when off the render thread.
                Runnable release = () -> {
                    Minecraft.getInstance().getTextureManager().release(textureId);
                    tex.close();
                };
                if (Minecraft.getInstance().isSameThread()) {
                    release.run();
                } else {
                    Minecraft.getInstance().execute(release);
                }
            }
            textureWidth = 0;
            textureHeight = 0;
            rgbaBuffer   = null;
            readBuffer   = null;
            currentSource = null;
            lastResolvedSource = null;
            originalWidth = 0;
            originalHeight = 0;
            frameSize     = 0;
            videoFps      = 0;
            seekPosition  = 0;
        }

        // Fresh pipeline always needs fresh PTS/clock state (the stderr thread
        // feeds a brand-new queue and re-normalizes pts to its first frame).
        ptsQueue      = new ConcurrentLinkedQueue<>();
        usePts        = true;
        ptsMissCount  = 0;
        firstPts      = Double.NaN;
        playbackStartNanos = 0;
    }

    /** Parse an ffprobe r_frame_rate value like "24/1" or "30000/1001". */
    private static double parseFrameRate(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        String[] parts = raw.split("/");
        if (parts.length == 2) {
            try {
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                return den > 0 ? num / den : 0;
            } catch (NumberFormatException ignored) {}
        }
        try { return Double.parseDouble(raw); } catch (NumberFormatException e) { return 0; }
    }
}