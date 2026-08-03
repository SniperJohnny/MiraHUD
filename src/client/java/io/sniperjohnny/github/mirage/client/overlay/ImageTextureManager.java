package io.sniperjohnny.github.mirage.client.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import io.sniperjohnny.github.mirage.Mirage;
import io.sniperjohnny.github.mirage.client.overlay.config.FilePathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages static image textures. Each overlay gets its own instance with a
 * unique texture identifier so multiple image overlays can render simultaneously.
 */
public class ImageTextureManager implements MediaProvider {
    private static final Map<String, ImageTextureManager> INSTANCES = new ConcurrentHashMap<>();

    /** Get or create a manager for a specific overlay (keyed by overlays config id). */
    public static ImageTextureManager forOverlay(String overlayId) {
        return INSTANCES.computeIfAbsent(overlayId, ImageTextureManager::new);
    }

    /** Remove and cleanup a previously created manager. */
    public static void removeOverlay(String overlayId) {
        ImageTextureManager mgr = INSTANCES.remove(overlayId);
        if (mgr != null) mgr.cleanup();
    }

    /** Clean up all instances (called on mod shutdown). */
    public static void cleanupAll() {
        for (ImageTextureManager mgr : INSTANCES.values()) mgr.cleanup();
        INSTANCES.clear();
    }

    // ---- Instance fields ----

    private final Identifier textureId;
    private DynamicTexture currentTexture;
    private String currentSource;
    private String lastFailedSource;
    private int originalWidth;
    private int originalHeight;

    private ImageTextureManager(String overlayId) {
        this.textureId = Identifier.fromNamespaceAndPath(
                Mirage.MOD_ID, "img_overlay_" + overlayId.replace("-", "_")
        );
    }

    // ---- MediaProvider implementation ----

    @Override
    public Identifier getTextureId() { return textureId; }

    @Override
    public boolean hasTexture() { return currentTexture != null; }

    @Override
    public String getCurrentSource() { return currentSource == null ? "" : currentSource; }

    @Override
    public int getOriginalWidth() { return originalWidth; }

    @Override
    public int getOriginalHeight() { return originalHeight; }

    @Override
    public boolean updateSource(String source) {
        if (source == null || source.isBlank()) {
            cleanup();
            currentSource = "";
            lastFailedSource = null;
            return true;
        }
        if (source.equals(currentSource)) return true;

        // Resolve exactly like the video provider does, so relative paths and
        // files outside the game directory (Downloads, Desktop, ...) work —
        // not just files relative to the game folder (screenshots).
        String resolved = FilePathUtil.resolve(source);
        Path filePath = Path.of(resolved);
        if (!Files.exists(filePath)) {
            Mirage.LOGGER.warn("Overlay image does not exist: {}", source);
            lastFailedSource = source;
            return false;
        }

        cleanup();

        NativeImage image = loadImage(filePath);
        if (image == null) {
            Mirage.LOGGER.error("Failed to load overlay image: {}", source);
            lastFailedSource = source;
            return false;
        }

        originalWidth = image.getWidth();
        originalHeight = image.getHeight();
        currentTexture = new DynamicTexture(textureId::toString, image);
        Minecraft.getInstance().getTextureManager().register(textureId, currentTexture);
        // Store the RAW source for the render loop's identity check (like
        // VideoTextureManager). FilePathUtil resolves at load time only.
        currentSource = source;
        lastFailedSource = null;
        return true;
    }

    /**
     * Decode an image file into a NativeImage.
     * <p>
     * Fast path: vanilla {@link NativeImage#read(InputStream)} (PNG/JPEG).
     * Fallback: ask FFmpeg (already bundled for video) to transcode any other
     * format — WEBP, TGA, BMP, GIF, AVIF, ... — to PNG, then decode that. This
     * is what makes downloaded web images show up, not just game screenshots.
     */
    private NativeImage loadImage(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath)) {
            NativeImage image = NativeImage.read(in);
            if (image != null) return image;
        } catch (IOException e) {
            // Unsupported/undecodable format (or IO error) — try FFmpeg below.
        }

        if (!ExternalToolManager.isFfmpegAvailable()) {
            Mirage.LOGGER.warn("Image decode fallback needs FFmpeg: {}", filePath.getFileName());
            return null;
        }

        List<String> cmd = List.of(
                ExternalToolManager.ffmpegCommand(),
                "-loglevel", "error",
                "-i", filePath.toString(),
                "-frames:v", "1",
                "-f", "image2pipe",
                "-vcodec", "png",
                "pipe:1"
        );
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process p = ExternalToolManager.startWithRetry(pb);

            // Drain stdout CONCURRENTLY with the process: FFmpeg blocks writing
            // the PNG to the pipe once the OS pipe buffer fills, and a PNG is
            // almost always larger than that buffer. Reading only after
            // waitFor() would deadlock until the timeout — so a daemon thread
            // drains while we wait.
            final byte[][] pngRef = new byte[1][];
            Thread drain = new Thread(() -> {
                try (InputStream in = p.getInputStream()) {
                    pngRef[0] = in.readAllBytes();
                } catch (IOException ignored) {
                    // pipe closed by destroyForcibly() — handled below
                }
            }, "Image-Decode-" + textureId.getPath());
            drain.setDaemon(true);
            drain.start();

            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                try { drain.join(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                Mirage.LOGGER.warn("FFmpeg image decode timed out for {}", filePath);
                return null;
            }
            try { drain.join(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            byte[] pngBytes = pngRef[0];
            if (pngBytes == null || pngBytes.length == 0) {
                Mirage.LOGGER.warn("FFmpeg produced no output for image {}", filePath);
                return null;
            }
            try (InputStream in = new ByteArrayInputStream(pngBytes)) {
                return NativeImage.read(in);
            }
        } catch (IOException | InterruptedException e) {
            Mirage.LOGGER.error("FFmpeg image decode failed for {}", filePath, e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void cleanup() {
        if (currentTexture != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            currentTexture.close();
            currentTexture = null;
            currentSource = null;
            lastFailedSource = null;
            originalWidth = 0;
            originalHeight = 0;
        }
    }

    public String getLastFailedSource() { return lastFailedSource; }

    @Override public boolean isVideo() { return false; }
    @Override public void tick() {}
    @Override public void playPause(boolean play) {}
    @Override public void setVolume(float volume) {}
    @Override public boolean isPlaying() { return false; }
}
