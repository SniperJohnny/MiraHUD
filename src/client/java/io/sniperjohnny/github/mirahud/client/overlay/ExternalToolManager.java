package io.sniperjohnny.github.mirahud.client.overlay;

import io.sniperjohnny.github.mirahud.MiraHUD;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Manages external tool detection (FFmpeg / FFprobe) with caching,
 * in-game toast notifications, and automatic download of a portable FFmpeg.
 * <p>
 * Resolution order:
 * <ol>
 *   <li><b>Bundled copy</b> in {@code config/mirahud/ffmpeg/} (auto-downloaded, preferred).</li>
 *   <li><b>PATH</b> — must be a reasonably modern build (major version ≥
 *       {@link #MIN_FFMPEG_MAJOR}). Very old builds (e.g. the user's 2013 build)
 *       lack {@code -err_detect ignore_err} and have Windows pipe bugs, so they
 *       are rejected and trigger the download instead.</li>
 * </ol>
 * <p>
 * The download runs on a background daemon thread (the game keeps playing). Once
 * finished, the tool cache is invalidated so the overlay retry loop picks the
 * bundled binaries up automatically. Download failures are throttled so a
 * missing internet connection doesn't hammer the network.
 * <p>
 * <b>Isolation guarantee:</b> the portable build is written <em>only</em> into
 * {@code config/mirahud/ffmpeg/} inside the client config folder. The
 * player's own FFmpeg installation (wherever it lives on disk / PATH) is never
 * modified, moved or deleted — it is only ever <em>executed</em> as a fallback
 * until the portable copy is ready. Deleting {@code config/mirahud/ffmpeg/}
 * restores the pre-download state completely.
 */
public class ExternalToolManager {

    /** How long to cache a PATH availability result (ms). */
    private static final long TOOL_CHECK_TTL_MS = 30_000;

    /** How often to re-check whether the bundled copy exists (ms). */
    private static final long BUNDLE_REFRESH_MS = 5_000;

    /** Minimum time between download attempts when a download fails (ms). */
    private static final long DOWNLOAD_RETRY_MS = 60_000;

    /** Portable FFmpeg zip — gyan.dev release essentials build (latest release). */
    private static final String FFMPEG_ZIP_URL =
            "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";

    /** @return true if running on Windows (OS name contains "Windows"). */
    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("windows");
    }

    /**
     * Minimum accepted FFmpeg major version on PATH.
     * The mod only needs flags available since 2.5 ({@code -err_detect ignore_err})
     * and 2.1 ({@code -fflags +discardcorrupt}), so 4.x builds pass. Older builds
     * (like the 2013 one observed on the user's machine) are rejected and
     * trigger the portable download instead.
     */
    private static final int MIN_FFMPEG_MAJOR = 4;

    // ---- Bundled copy paths (config/mirahud/ffmpeg/) ----
    private static final Path BUNDLE_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID)
            .resolve("ffmpeg");

    /** Platform-appropriate binary name: "ffmpeg.exe" on Windows, "ffmpeg" elsewhere. */
    private static final String EXE_SUFFIX = isWindows() ? ".exe" : "";
    private static final Path BUNDLED_FFMPEG = BUNDLE_DIR.resolve("ffmpeg" + EXE_SUFFIX);
    private static final Path BUNDLED_FFPROBE = BUNDLE_DIR.resolve("ffprobe" + EXE_SUFFIX);

    private static volatile Boolean bundledAvailable = null;
    private static volatile long bundledCheckedAt = 0;

    private static volatile Boolean ffmpegCached = null;
    private static volatile long ffmpegCachedAt = 0;
    private static volatile Boolean ffprobeCached = null;
    private static volatile long ffprobeCachedAt = 0;

    private static final AtomicBoolean downloadInProgress = new AtomicBoolean(false);
    private static volatile long lastDownloadAttemptAt = 0;

    /** Throttle for the repeated "FFmpeg Required" toasts after a failed download (ms). */
    private static final long MISSING_TOAST_THROTTLE_MS = 60_000;
    private static volatile long lastMissingToastAt = 0;

    // ==================== Binary resolution ====================

    /**
     * Command (or absolute path) to use for FFmpeg. Prefers the bundled copy
     * inside the client config folder ({@code config/mirahud/ffmpeg/}),
     * falls back to "ffmpeg" on PATH. Never modifies the player's own FFmpeg.
     */
    public static String ffmpegCommand() {
        return bundledFfmpegExists() ? BUNDLED_FFMPEG.toString() : "ffmpeg";
    }

    /**
     * Command (or absolute path) to use for FFprobe. Prefers the bundled copy
     * inside the client config folder, falls back to "ffprobe" on PATH.
     */
    public static String ffprobeCommand() {
        return bundledFfmpegExists() ? BUNDLED_FFPROBE.toString() : "ffprobe";
    }

    // ==================== Availability ====================

    /**
     * Check if a usable FFmpeg is available (bundled copy, or modern build on PATH).
     * If none is available, starts a background download of the portable build.
     */
    public static boolean isFfmpegAvailable() {
        if (bundledFfmpegExists()) return true;

        long now = System.currentTimeMillis();
        if (ffmpegCached != null && now - ffmpegCachedAt < TOOL_CHECK_TTL_MS) return ffmpegCached;

        boolean available = false;
        if (checkCommand("ffmpeg", "-version")) {
            available = ffmpegMajorVersion() >= MIN_FFMPEG_MAJOR;
            if (!available) {
                MiraHUD.LOGGER.warn("FFmpeg on PATH is too old (needs >= {}). Downloading a portable build.", MIN_FFMPEG_MAJOR);
            }
        }
        ffmpegCached = available;
        ffmpegCachedAt = now;

        if (!available) {
            if (isWindows()) {
                MiraHUD.LOGGER.warn("No usable FFmpeg found. Starting portable build download...");
                maybeStartDownload();
            } else {
                MiraHUD.LOGGER.warn("No usable FFmpeg found. Install FFmpeg via your package manager (apt, brew, pacman, etc.) and ensure it is on PATH.");
                showToast("FFmpeg Required", "Install FFmpeg via your package manager and add it to PATH.");
            }
        }
        return available;
    }

    /**
     * Check if FFprobe is available (bundled copy, or on PATH).
     * FFprobe ships inside the same portable zip, so if FFmpeg triggers a
     * download, ffprobe will be available afterwards too.
     */
    public static boolean isFfprobeAvailable() {
        if (bundledFfmpegExists()) return true;

        long now = System.currentTimeMillis();
        if (ffprobeCached != null && now - ffprobeCachedAt < TOOL_CHECK_TTL_MS) return ffprobeCached;

        boolean available = checkCommand("ffprobe", "-version");
        ffprobeCached = available;
        ffprobeCachedAt = now;

        if (!available) {
            MiraHUD.LOGGER.warn("FFprobe not found. It is bundled with the portable FFmpeg download.");
        }
        return available;
    }

    /** @return true if a portable FFmpeg download is currently in progress. */
    public static boolean isDownloadInProgress() {
        return downloadInProgress.get();
    }

    /** Invalidate cached tool availability. */
    public static void invalidateCache() {
        ffmpegCached = null;
        ffprobeCached = null;
        bundledAvailable = null;
    }

    // ==================== Bundled copy ====================

    private static boolean bundledFfmpegExists() {
        // Never treat partially-extracted binaries as usable: while a download is
        // in progress, ffmpeg.exe / ffprobe.exe exist on disk but are still locked
        // by the extraction thread's Files.copy — spawning them then fails with
        // Windows "CreateProcess error=32" (ERROR_SHARING_VIOLATION).
        if (downloadInProgress.get()) return false;
        long now = System.currentTimeMillis();
        if (bundledAvailable != null && now - bundledCheckedAt < BUNDLE_REFRESH_MS) return bundledAvailable;
        bundledAvailable = Files.isRegularFile(BUNDLED_FFMPEG) && Files.isRegularFile(BUNDLED_FFPROBE);
        bundledCheckedAt = now;
        return bundledAvailable;
    }
    // ==================== Download ====================

    private static void maybeStartDownload() {
        long now = System.currentTimeMillis();
        if (!downloadInProgress.compareAndSet(false, true)) return;
        if (now - lastDownloadAttemptAt < DOWNLOAD_RETRY_MS) {
            downloadInProgress.set(false);
            return;
        }
        lastDownloadAttemptAt = now;

        showToast("Downloading FFmpeg…", "Portable build (~100 MB). You can keep playing.");

        Thread thread = new Thread(() -> {
            boolean ok = false;
            try {
                ok = downloadAndExtractBundled();
            } catch (Throwable t) {
                MiraHUD.LOGGER.error("FFmpeg download failed", t);
            }
            if (ok) {
                invalidateCache();
                MiraHUD.LOGGER.info("Portable FFmpeg installed at {} (player's own FFmpeg is untouched)", BUNDLE_DIR);
                showToast("FFmpeg ready", "Retrying the video automatically.");
            } else {
                MiraHUD.LOGGER.error("FFmpeg download failed. Install FFmpeg manually and add it to PATH.");
                showToast("FFmpeg download failed",
                        isWindows() ? "Install FFmpeg manually and add it to PATH."
                                : "Install FFmpeg via your package manager and add it to PATH.");
            }
            // Release the flag only after the cache was updated (or the failure
            // recorded), so a concurrent availability check never observes a
            // stale cache with the download already "done".
            downloadInProgress.set(false);
        }, "FFmpeg-Download");
        thread.setDaemon(true);
        thread.start();
    }

    /** Download the gyan.dev essentials zip and extract ffmpeg.exe + ffprobe.exe. */
    private static boolean downloadAndExtractBundled() throws IOException {
        Files.createDirectories(BUNDLE_DIR);

        URL url = new URL(FFMPEG_ZIP_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (mirahud)");

        try (InputStream in = conn.getInputStream();
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in, 64 * 1024))) {
            ZipEntry entry;
            boolean gotFfmpeg = false;
            boolean gotFfprobe = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                // Zip paths use forward slashes: e.g. ffmpeg-8.1.2-essentials_build/bin/ffmpeg.exe
                if (name.endsWith("/ffmpeg.exe")) {
                    Files.copy(zis, BUNDLED_FFMPEG, StandardCopyOption.REPLACE_EXISTING);
                    gotFfmpeg = true;
                } else if (name.endsWith("/ffprobe.exe")) {
                    Files.copy(zis, BUNDLED_FFPROBE, StandardCopyOption.REPLACE_EXISTING);
                    gotFfprobe = true;
                }
                if (gotFfmpeg && gotFfprobe) break;
            }
            boolean valid;
            try {
                valid = gotFfmpeg && gotFfprobe
                        && Files.size(BUNDLED_FFMPEG) > 0
                        && Files.size(BUNDLED_FFPROBE) > 0;
            } catch (IOException e) {
                // A missing/unreadable binary must never count as a valid install.
                valid = false;
            }
            if (!valid) {
                // Never leave partial/0-byte binaries behind: they would pass the
                // isRegularFile check and silently break playback later.
                try { Files.deleteIfExists(BUNDLED_FFMPEG); } catch (IOException ignored) {}
                try { Files.deleteIfExists(BUNDLED_FFPROBE); } catch (IOException ignored) {}
            }
            return valid;
        }
    }

    // ==================== Version detection ====================

    /**
     * Parse the FFmpeg major version from {@code ffmpeg -version}.
     * @return major version (e.g. 8), or a value below {@link #MIN_FFMPEG_MAJOR}
     *         for ancient/git master builds whose version string doesn't start
     *         with a number (e.g. the 2013 build "N-55702-g920046a").
     */
    private static int ffmpegMajorVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = startWithRetry(pb);
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return 0;
            }
            String out = new String(p.getInputStream().readAllBytes());
            String firstLine = out.lines().findFirst().orElse("");
            // "ffmpeg version 8.1.2-essentials_build ..." → 8
            Matcher m = Pattern.compile("ffmpeg version (\\d+)").matcher(firstLine);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            return 0; // e.g. "ffmpeg version N-55702-g920046a" → ancient
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== Toast notifications ====================

    public static void showMissingFfmpegToast() {
        // Throttle: after a failed download the render loop retries every 5s —
        // only remind the player once per minute, not on every retry.
        long now = System.currentTimeMillis();
        if (now - lastMissingToastAt < MISSING_TOAST_THROTTLE_MS) return;
        lastMissingToastAt = now;
        showToast("FFmpeg Required", "A portable build is downloading. Otherwise install FFmpeg and add it to PATH.");
    }

    public static void showMissingFfprobeToast() {
        long now = System.currentTimeMillis();
        if (now - lastMissingToastAt < MISSING_TOAST_THROTTLE_MS) return;
        lastMissingToastAt = now;
        showToast("FFprobe Required", "FFprobe (bundled with FFmpeg) is needed to read video dimensions.");
    }

    private static void showToast(String title, String message) {
        Minecraft.getInstance().execute(() ->
            SystemToast.add(Minecraft.getInstance().getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(title),
                Component.literal(message))
        );
    }

    // ==================== Utilities ====================

    private static boolean checkCommand(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = startWithRetry(pb);
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            // Drain remaining output after exit (readAllBytes blocks until EOF)
            p.getInputStream().readAllBytes();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Process spawning ====================

    /**
     * Start a process, retrying on Windows {@code ERROR_SHARING_VIOLATION}
     * ({@code CreateProcess error=32}). This happens when the executable is
     * momentarily locked — e.g. right after it was extracted to disk, or while
     * an antivirus scanner is inspecting the freshly-downloaded binary.
     *
     * @param pb the process builder to start
     * @return the started process
     * @throws IOException if the process could not be started after all retries
     */
    static Process startWithRetry(ProcessBuilder pb) throws IOException {
        IOException last = new IOException("Process start failed");
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return pb.start();
            } catch (IOException e) {
                last = e;
                if (!isSharingViolation(e) || attempt == 3) break;
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    /** @return true if the exception is a Windows sharing violation (file locked). */
    private static boolean isSharingViolation(IOException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("CreateProcess error=32");
    }
}
