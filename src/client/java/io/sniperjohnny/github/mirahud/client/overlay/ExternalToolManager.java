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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
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

    // A real ffmpeg/ffprobe binary is tens of MB; anything smaller is an HTML
    // error page, a partial write or an empty file and is treated as corrupt.
    private static final long MIN_EXECUTABLE_SIZE = 1_000_000;

    // Windows: a single zip (gyan.dev) carries both ffmpeg.exe and ffprobe.exe.
    private static final String FFMPEG_ZIP_URL =
            "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";

    // Linux + macOS: Martin Riedl's build server provides per-platform static
    // zip downloads of ffmpeg and ffprobe, for amd64 and arm64.
    // Pattern: https://ffmpeg.martin-riedl.de/redirect/latest/{macos,linux}/{amd64,arm64}/snapshot/{ffmpeg.zip,ffprobe.zip}
    private static final String MARTIN_RIEDL_BASE =
            "https://ffmpeg.martin-riedl.de/redirect/latest/";

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("windows");
    }

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("mac") || os.contains("darwin");
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("linux");
    }

    private static boolean isArm64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        return arch.contains("aarch64") || arch.contains("arm64");
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

    /**
     * Checks at game startup whether a usable FFmpeg/FFprobe is available and,
     * if not, quietly starts the portable download in the background. Never
     * blocks the game: the whole check (PATH probing included) runs off-thread.
     */
    public static void ensureAvailableOnStartup() {
        Thread check = new Thread(() -> {
            try {
                if (bundledFfmpegExists()) return;
                isFfmpegAvailable();
                isFfprobeAvailable();
            } catch (Throwable ignored) {
            }
        }, "FFmpeg-StartupCheck");
        check.setDaemon(true);
        check.start();
    }

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
            if (isWindows() || isMac() || isLinux()) {
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

    /**
     * True only when the bundled binaries exist AND look like real executables.
     * Corrupt or partial leftovers are deleted here so a fresh download starts
     * instead of the game trying to run garbage (CreateProcess error 193).
     */
    private static boolean bundledFfmpegExists() {
        if (downloadInProgress.get()) return false;
        long now = System.currentTimeMillis();
        if (bundledAvailable != null && now - bundledCheckedAt < BUNDLE_REFRESH_MS) return bundledAvailable;

        boolean ffOk = Files.isRegularFile(BUNDLED_FFMPEG) && isValidExecutable(BUNDLED_FFMPEG);
        boolean fpOk = Files.isRegularFile(BUNDLED_FFPROBE) && isValidExecutable(BUNDLED_FFPROBE);
        if (!ffOk || !fpOk) {
            if (Files.exists(BUNDLED_FFMPEG)) tryDelete(BUNDLED_FFMPEG);
            if (Files.exists(BUNDLED_FFPROBE)) tryDelete(BUNDLED_FFPROBE);
            bundledAvailable = false;
        } else {
            bundledAvailable = true;
        }
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
            if (!ok) cleanupPartials();
            if (ok) {
                invalidateCache();
                MiraHUD.LOGGER.info("Portable FFmpeg installed at {} (player's own FFmpeg is untouched)", BUNDLE_DIR);
                showToast(TranslationsKeys.TOAST_FFMPEG_READY_TITLE, TranslationsKeys.TOAST_FFMPEG_READY_MESSAGE);
            } else {
                MiraHUD.LOGGER.error("FFmpeg download failed. Install FFmpeg manually and add it to PATH.");
                showToast(TranslationsKeys.TOAST_FFMPEG_DOWNLOAD_FAILED_TITLE,
                        isWindows() || isMac() || isLinux()
                                ? TranslationsKeys.TOAST_FFMPEG_DOWNLOAD_FAILED_MESSAGE_MANUAL
                                : TranslationsKeys.TOAST_FFMPEG_DOWNLOAD_FAILED_MESSAGE_PACKAGE);
            }
            downloadInProgress.set(false);
        }, "FFmpeg-Download");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean downloadAndExtractBundled() throws IOException {
        Files.createDirectories(BUNDLE_DIR);
        if (isWindows()) {
            // One zip (gyan.dev) carries both ffmpeg.exe and ffprobe.exe.
            return extractFromZip(FFMPEG_ZIP_URL, List.of(BUNDLED_FFMPEG, BUNDLED_FFPROBE));
        }
        // Linux/macOS: separate zips for ffmpeg and ffprobe.
        boolean ffmpegOk = downloadExecutableZip(ffmpegZipUrl(), BUNDLED_FFMPEG);
        boolean ffprobeOk = downloadExecutableZip(ffprobeZipUrl(), BUNDLED_FFPROBE);
        if (!ffmpegOk) tryDelete(BUNDLED_FFMPEG);
        if (!ffprobeOk) tryDelete(BUNDLED_FFPROBE);
        return ffmpegOk && ffprobeOk;
    }

    private static String ffmpegZipUrl() {
        if (isWindows()) return FFMPEG_ZIP_URL;
        return MARTIN_RIEDL_BASE + (isMac() ? "macos" : "linux") + "/" + (isArm64() ? "arm64" : "amd64") + "/snapshot/ffmpeg.zip";
    }

    private static String ffprobeZipUrl() {
        if (isWindows()) return FFMPEG_ZIP_URL;
        return MARTIN_RIEDL_BASE + (isMac() ? "macos" : "linux") + "/" + (isArm64() ? "arm64" : "amd64") + "/snapshot/ffprobe.zip";
    }

    private static boolean extractFromZip(String zipUrl, List<Path> targets) throws IOException {
        URL url = new URL(zipUrl);
        try (InputStream in = openConnectionStream(url);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in, 64 * 1024))) {
            boolean[] got = new boolean[targets.size()];
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                for (int i = 0; i < targets.size(); i++) {
                    if (got[i]) continue;
                    if (entryMatches(name, targets.get(i).getFileName().toString())) {
                        got[i] = saveValidated(zis, targets.get(i));
                    }
                }
            }
            boolean all = true;
            for (int i = 0; i < targets.size(); i++) {
                if (!got[i]) {
                    tryDelete(targets.get(i));
                    all = false;
                }
            }
            return all;
        }
    }

    private static boolean downloadExecutableZip(String zipUrl, Path target) throws IOException {
        URL url = new URL(zipUrl);
        String want = target.getFileName().toString();
        try (InputStream in = openConnectionStream(url);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in, 64 * 1024))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entryMatches(entry.getName(), want)) {
                    return saveValidated(zis, target);
                }
            }
        }
        return false;
    }

    private static boolean entryMatches(String entryName, String fileName) {
        return entryName.equals(fileName) || entryName.endsWith("/" + fileName);
    }

    /**
     * Copies the current zip entry to a .part file, validates that it is a real
     * executable, and only then moves it into place. A corrupt or interrupted
     * download therefore never leaves a half-written binary behind.
     */
    private static boolean saveValidated(InputStream zis, Path target) throws IOException {
        Path part = BUNDLE_DIR.resolve(target.getFileName() + ".part");
        Files.deleteIfExists(part);
        Files.copy(zis, part, StandardCopyOption.REPLACE_EXISTING);
        if (!isValidExecutable(part)) {
            Files.deleteIfExists(part);
            return false;
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        makeExecutable(target);
        return true;
    }

    private static InputStream openConnectionStream(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (mirahud)");
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + code + " for " + url);
        }
        return conn.getInputStream();
    }

    /**
     * Platform-aware sanity check: a minimum size plus the correct executable
     * magic bytes (PE on Windows, ELF on Linux, Mach-O on macOS). On Windows the
     * PE section table is also checked so truncated files are caught.
     */
    private static boolean isValidExecutable(Path p) {
        try {
            long size = Files.size(p);
            if (size < MIN_EXECUTABLE_SIZE) return false;
            byte[] head;
            try (InputStream in = Files.newInputStream(p)) {
                head = in.readNBytes(64);
            }
            if (isWindows()) {
                if (head.length < 2 || head[0] != 'M' || head[1] != 'Z') return false;
                return isValidPe(p, size);
            }
            if (isMac()) {
                if (head.length < 4) return false;
                int b0 = head[0] & 0xFF, b1 = head[1] & 0xFF, b2 = head[2] & 0xFF, b3 = head[3] & 0xFF;
                return (b0 == 0xFE && b1 == 0xED && b2 == 0xFA && (b3 == 0xCE || b3 == 0xCF))
                        || (b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE)
                        || (b0 == 0xBE && b1 == 0xBA && b2 == 0xFE && b3 == 0xCA);
            }
            if (isLinux()) {
                return head.length >= 4 && head[0] == 0x7F && head[1] == 'E' && head[2] == 'L' && head[3] == 'F';
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Checks every PE section's raw data range against the file size so that a
     * download cut short in the middle of a section is flagged as corrupt
     * instead of failing later with "not a valid Win32 application".
     */
    private static boolean isValidPe(Path p, long fileSize) {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] hdr = in.readNBytes(4096);
            if (hdr.length < 64) return false;
            ByteBuffer buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN);
            int peOff = buf.getInt(0x3C);
            if (peOff < 0 || peOff + 24 > hdr.length) return false;
            if (buf.getInt(peOff) != 0x00004550) return false; // "PE\0\0"
            int nSections = buf.getShort(peOff + 6) & 0xFFFF;
            int optSize = buf.getShort(peOff + 20) & 0xFFFF;
            int secStart = peOff + 24 + optSize;
            if (secStart < 0 || secStart + nSections * 40 > hdr.length) return false;
            for (int i = 0; i < nSections; i++) {
                int base = secStart + i * 40;
                long rawSize = buf.getInt(base + 16) & 0xFFFFFFFFL;
                long rawPtr = buf.getInt(base + 20) & 0xFFFFFFFFL;
                if (rawPtr + rawSize > fileSize) return false;
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void makeExecutable(Path p) {
        if (isWindows()) return;
        try {
            Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (Exception ignored) {
        }
    }

    private static void cleanupPartials() {
        for (String name : new String[]{
                BUNDLED_FFMPEG.getFileName().toString() + ".part",
                BUNDLED_FFPROBE.getFileName().toString() + ".part"}) {
            try {
                Files.deleteIfExists(BUNDLE_DIR.resolve(name));
            } catch (IOException ignored) {
            }
        }
    }

    private static void tryDelete(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
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