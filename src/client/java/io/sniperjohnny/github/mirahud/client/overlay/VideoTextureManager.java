package io.sniperjohnny.github.mirahud.client.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.overlay.config.FilePathUtil;
import io.sniperjohnny.github.mirahud.client.overlay.config.VideoConfigManager;
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

public class VideoTextureManager implements MediaProvider {
    private final Identifier textureId;
    private final String overlayId;

    private DynamicTexture dynTexture;
    private NativeImage nativeImage;
    private String currentSource;
    private String lastResolvedSource;
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
    private volatile boolean finishedNaturally = false;
    private volatile boolean loop = false;
    private volatile double videoFps = 0;

    private ByteBuffer rgbaBuffer;
    private byte[] readBuffer;
    private int textureWidth = 0;
    private int textureHeight = 0;

    private static final Pattern FRAME_N_PATTERN = Pattern.compile("n:\\s*(\\d+)");
    private static final Pattern PTS_PATTERN = Pattern.compile("pts_time:(-?[0-9]+(?:\\.[0-9]+)?)");

    private record PtsEntry(int frameIndex, double ptsTime) {}

    private volatile ConcurrentLinkedQueue<PtsEntry> ptsQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean usePts = true;
    private int ptsMissCount = 0;
    private static final int PTS_MISS_LIMIT = 6;
    private static final long PTS_POLL_NS = 100_000_000L;
    private static final double AUDIO_SYNC_TOLERANCE_S = 0.005;
    private double firstPts = Double.NaN;
    private long playbackStartNanos = 0;

    public VideoTextureManager(String overlayId) {
        this.overlayId = overlayId;
        this.textureId = Identifier.fromNamespaceAndPath(
                MiraHUD.MOD_ID, "video_overlay_" + overlayId.replace("-", "_")
        );
    }

    @Override public Identifier getTextureId() { return textureId; }
    @Override public boolean hasTexture() { return dynTexture != null; }
    @Override public String getCurrentSource() { return currentSource == null ? "" : currentSource; }
    @Override public int getOriginalWidth() { return originalWidth; }
    @Override public int getOriginalHeight() { return originalHeight; }
    @Override public boolean isVideo() { return true; }
    @Override public boolean isPlaying() { return playing.get(); }

    @Override
    public boolean isAlive() {
        if (finishedNaturally && loop) return false;
        return running.get() || finishedNaturally;
    }

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
        if (playbackStartNanos > 0) {
            return (System.nanoTime() - playbackStartNanos) / 1_000_000_000.0 + seekPosition;
        }
        return seekPosition;
    }

    @Override
    public boolean updateSource(String source) {
        if (source == null || source.isBlank()) { cleanup(); return true; }
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

    private void startFfmpeg(String source) throws IOException {
        if (!probeDimensions(source)) {
            throw new IOException("Failed to probe video dimensions for " + source);
        }

        int maxHeight = VideoConfigManager.getConfig().maxVideoHeight;
        boolean needsScale = maxHeight > 0 && originalHeight > maxHeight;
        if (needsScale) {
            float scale = (float) maxHeight / originalHeight;
            originalWidth  = Math.max(2, Math.round(originalWidth  * scale)) & ~1;
            originalHeight = Math.max(2, Math.round(originalHeight * scale)) & ~1;
            MiraHUD.LOGGER.info("Scaling video to {}x{} for overlay playback", originalWidth, originalHeight);
        }

        frameSize = originalWidth * originalHeight * 4;

        final int w = originalWidth, h = originalHeight;
        Runnable createTexture = () -> {
            if (dynTexture != null && nativeImage != null
                    && textureWidth == w && textureHeight == h) {
                return;
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

            nativeImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            dynTexture.setPixels(nativeImage);
            // upload() re-uploads NativeImage memory each call, so per-frame writes in tick() are visible.
            dynTexture.upload();
            textureWidth = w;
            textureHeight = h;
        };
        if (Minecraft.getInstance().isSameThread()) {
            createTexture.run();
        } else {
            Minecraft.getInstance().execute(createTexture);
        }

        if (rgbaBuffer == null || rgbaBuffer.capacity() != frameSize) {
            rgbaBuffer = ByteBuffer.allocateDirect(frameSize);
            readBuffer = new byte[frameSize];
        }

        List<String> vCmd = new ArrayList<>();
        vCmd.add(ExternalToolManager.ffmpegCommand()); vCmd.add("-loglevel"); vCmd.add("info");
        if (VideoConfigManager.getConfig().ffmpegHardwareAccel) {
            // Opt-in: -hwaccel auto crashes on some Windows GPU/driver combos.
            vCmd.add("-hwaccel"); vCmd.add("auto");
        }
        // No -re here: the audio clock paces frames below; -re throttles the pipe and races startup.
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
        ffmpegVideoProcess = ExternalToolManager.startWithRetry(vPb);

        final ConcurrentLinkedQueue<PtsEntry> queue = new ConcurrentLinkedQueue<>();
        this.ptsQueue = queue;
        InputStream stderr = ffmpegVideoProcess.getErrorStream();
        Thread ptsThread = new Thread(() -> parseShowInfo(stderr, queue), "Video-Pts-" + overlayId);
        ptsThread.setDaemon(true);
        ptsThread.start();

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

        InputStream videoStream = new BufferedInputStream(ffmpegVideoProcess.getInputStream(), 65536);
        frameReaderThread = new Thread(() -> readFrames(videoStream), "Video-Frames-" + overlayId);
        frameReaderThread.setDaemon(true);
        frameReaderThread.start();
    }

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

    private void readFrames(InputStream videoStream) {
        int framesRead = 0;
        try (DataInputStream dataIn = new DataInputStream(videoStream)) {
            while (running.get()) {
                ByteBuffer localRgba = rgbaBuffer;
                byte[] localRead = readBuffer;
                int localFrameSize = frameSize;
                if (localRgba == null || localRead == null || localFrameSize <= 0) break;

                dataIn.readFully(localRead, 0, localFrameSize);
                framesRead++;

                if (framesRead == 1) {
                    playbackStartNanos = System.nanoTime();
                    if (audioStreamer != null) audioStreamer.start();
                    MiraHUD.LOGGER.info("Video frame 1 ready, audio started");
                }

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
                    finishedNaturally = true;
                    MiraHUD.LOGGER.info("Video playback ended after {} frames", framesRead);
                } else {
                    MiraHUD.LOGGER.warn("Video frame read error after {} frames ({}): {}",
                            framesRead, e.getClass().getSimpleName(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    private void paceFrame(int framesRead) {
        if (videoFps <= 0) return;
        int frameIndex = framesRead - 1;

        boolean audioAudible = audioStreamer != null && audioStreamer.isActive() && !audioStreamer.isEof();

        double target = frameIndex / videoFps;

        if (audioAudible && usePts) {
            double pts = pollPts(frameIndex);
            if (pts >= 0) {
                ptsMissCount = 0;
                target = pts;
            } else if (++ptsMissCount >= PTS_MISS_LIMIT) {
                usePts = false;
                MiraHUD.LOGGER.warn("Frame timestamps unavailable for overlay {}; using frame-count pacing.", overlayId);
            }
        }

        double clockNow = audioAudible
                ? audioStreamer.getPlaybackPositionSeconds()
                : (System.nanoTime() - playbackStartNanos) / 1_000_000_000.0;

        double ahead = target - clockNow;
        if (ahead > AUDIO_SYNC_TOLERANCE_S) {
            sleepWhileRunning((long) ((ahead - AUDIO_SYNC_TOLERANCE_S) * 1_000_000_000.0));
        }
    }

    private double pollPts(int frameIndex) {
        long deadline = System.nanoTime() + PTS_POLL_NS;
        while (running.get() && System.nanoTime() < deadline) {
            PtsEntry entry = ptsQueue.peek();
            if (entry != null) {
                if (entry.frameIndex < frameIndex) {
                    ptsQueue.poll();
                    continue;
                }
                if (entry.frameIndex == frameIndex) {
                    ptsQueue.poll();
                    return entry.ptsTime;
                }
                return -1;
            }
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                break;
            }
        }
        return -1;
    }

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
                        if (Double.isNaN(firstPts)) firstPts = pts;
                        queue.add(new PtsEntry(n, pts - firstPts));
                    } catch (NumberFormatException ignored) {
                    }
                } else if (isFfmpegErrorLine(line)) {
                    MiraHUD.LOGGER.error("[ffmpeg] {}", line);
                } else {
                    MiraHUD.LOGGER.debug("[ffmpeg] {}", line);
                }
            }
        } catch (IOException e) {
        }
    }

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

    private void sleepWhileRunning(long nanos) {
        long deadline = System.nanoTime() + nanos;
        while (running.get()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try {
                Thread.sleep(Math.min(remaining / 1_000_000L, 10L));
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @Override
    public void tick() {
        if (!running.get() || !playing.get()) return;
        if (dynTexture == null || nativeImage == null) return;
        if (frameSize <= 0) return;

        synchronized (frameLock) {
            if (!frameReady) return;
            frameReady = false;

            ByteBuffer localRgba = rgbaBuffer;
            if (localRgba == null) return;
            ByteBuffer nativeBuf = MemoryUtil.memByteBuffer(nativeImage.getPointer(), frameSize);
            nativeBuf.clear();
            nativeBuf.put(localRgba);
            nativeBuf.flip();
        }

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
        if (clamped == this.volume) return;
        this.volume = clamped;
        if (audioStreamer != null) audioStreamer.setVolume(this.volume);
    }

    @Override
    public void cleanup() {
        teardown(false);
    }

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

        if (oldReader != null) {
            try { oldReader.join(2000); } catch (InterruptedException ignored) {}
        }

        frameReady = false;
        finishedNaturally = false;

        if (!preserveTexture) {
            if (nativeImage != null) {
                nativeImage.close();
                nativeImage = null;
            }
            if (dynTexture != null) {
                DynamicTexture tex = dynTexture;
                dynTexture = null;
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

        ptsQueue      = new ConcurrentLinkedQueue<>();
        usePts        = true;
        ptsMissCount  = 0;
        firstPts      = Double.NaN;
        playbackStartNanos = 0;
    }

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
