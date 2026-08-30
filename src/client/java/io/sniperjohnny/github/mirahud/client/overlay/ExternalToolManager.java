package io.sniperjohnny.github.mirahud.client.overlay;

import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
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

public class ExternalToolManager {

    private static final long TOOL_CHECK_TTL_MS = 30_000;

    private static final long BUNDLE_REFRESH_MS = 5_000;

    private static final long DOWNLOAD_RETRY_MS = 60_000;

    private static final String FFMPEG_ZIP_URL =
            "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("windows");
    }

    private static final int MIN_FFMPEG_MAJOR = 4;

    private static final Path BUNDLE_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID)
            .resolve("ffmpeg");

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

    private static final long MISSING_TOAST_THROTTLE_MS = 60_000;
    private static volatile long lastMissingToastAt = 0;

    public static String ffmpegCommand() {
        return bundledFfmpegExists() ? BUNDLED_FFMPEG.toString() : "ffmpeg";
    }

    public static String ffprobeCommand() {
        return bundledFfmpegExists() ? BUNDLED_FFPROBE.toString() : "ffprobe";
    }

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
                showToast(TranslationsKeys.TOAST_FFMPEG_REQUIRED_TITLE, TranslationsKeys.TOAST_FFMPEG_REQUIRED_MESSAGE_INSTALL);
            }
        }
        return available;
    }

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

    public static boolean isDownloadInProgress() {
        return downloadInProgress.get();
    }

    public static void invalidateCache() {
        ffmpegCached = null;
        ffprobeCached = null;
        bundledAvailable = null;
    }

    private static boolean bundledFfmpegExists() {
        if (downloadInProgress.get()) return false;
        long now = System.currentTimeMillis();
        if (bundledAvailable != null && now - bundledCheckedAt < BUNDLE_REFRESH_MS) return bundledAvailable;
        bundledAvailable = Files.isRegularFile(BUNDLED_FFMPEG) && Files.isRegularFile(BUNDLED_FFPROBE);
        bundledCheckedAt = now;
        return bundledAvailable;
    }

    private static void maybeStartDownload() {
        long now = System.currentTimeMillis();
        if (!downloadInProgress.compareAndSet(false, true)) return;
        if (now - lastDownloadAttemptAt < DOWNLOAD_RETRY_MS) {
            downloadInProgress.set(false);
            return;
        }
        lastDownloadAttemptAt = now;

        showToast(TranslationsKeys.TOAST_FFMPEG_DOWNLOADING_TITLE, TranslationsKeys.TOAST_FFMPEG_DOWNLOADING_MESSAGE);

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
                showToast(TranslationsKeys.TOAST_FFMPEG_READY_TITLE, TranslationsKeys.TOAST_FFMPEG_READY_MESSAGE);
            } else {
                MiraHUD.LOGGER.error("FFmpeg download failed. Install FFmpeg manually and add it to PATH.");
                showToast(TranslationsKeys.TOAST_FFMPEG_DOWNLOAD_FAILED_TITLE,
                        isWindows() ? TranslationsKeys.TOAST_FFMPEG_DOWNLOAD_FAILED_MESSAGE_MANUAL
                                : TranslationsKeys.TOAST_FFMPEG_DOWNLOAD_FAILED_MESSAGE_PACKAGE);
            }
            downloadInProgress.set(false);
        }, "FFmpeg-Download");
        thread.setDaemon(true);
        thread.start();
    }

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
                valid = false;
            }
            if (!valid) {
                try { Files.deleteIfExists(BUNDLED_FFMPEG); } catch (IOException ignored) {}
                try { Files.deleteIfExists(BUNDLED_FFPROBE); } catch (IOException ignored) {}
            }
            return valid;
        }
    }

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
            Matcher m = Pattern.compile("ffmpeg version (\\d+)").matcher(firstLine);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void showMissingFfmpegToast() {
        long now = System.currentTimeMillis();
        if (now - lastMissingToastAt < MISSING_TOAST_THROTTLE_MS) return;
        lastMissingToastAt = now;
        showToast(TranslationsKeys.TOAST_FFMPEG_REQUIRED_TITLE, TranslationsKeys.TOAST_FFMPEG_REQUIRED_MESSAGE_DOWNLOADING);
    }

    public static void showMissingFfprobeToast() {
        long now = System.currentTimeMillis();
        if (now - lastMissingToastAt < MISSING_TOAST_THROTTLE_MS) return;
        lastMissingToastAt = now;
        showToast(TranslationsKeys.TOAST_FFPROBE_REQUIRED_TITLE, TranslationsKeys.TOAST_FFPROBE_REQUIRED_MESSAGE);
    }

    private static void showToast(String titleKey, String messageKey) {
        Minecraft.getInstance().execute(() ->
            SystemToast.add(Minecraft.getInstance().getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable(titleKey),
                Component.translatable(messageKey))
        );
    }

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
            p.getInputStream().readAllBytes();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static final int PROCESS_START_RETRIES = 3;
    private static final long PROCESS_START_RETRY_BACKOFF_MS = 500;

    static Process startWithRetry(ProcessBuilder pb) throws IOException {
        IOException last = new IOException("Process start failed");
        for (int attempt = 1; attempt <= PROCESS_START_RETRIES; attempt++) {
            try {
                return pb.start();
            } catch (IOException e) {
                last = e;
                if (!isSharingViolation(e) || attempt == PROCESS_START_RETRIES) break;
                try {
                    Thread.sleep(PROCESS_START_RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw last;
    }

    private static boolean isSharingViolation(IOException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("CreateProcess error=32");
    }
}
