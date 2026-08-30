package io.sniperjohnny.github.mirahud.client.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.overlay.config.FilePathUtil;
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

public class ImageTextureManager implements MediaProvider {
    private static final Map<String, ImageTextureManager> INSTANCES = new ConcurrentHashMap<>();

    public static ImageTextureManager forOverlay(String overlayId) {
        return INSTANCES.computeIfAbsent(overlayId, ImageTextureManager::new);
    }

    public static void removeOverlay(String overlayId) {
        ImageTextureManager mgr = INSTANCES.remove(overlayId);
        if (mgr != null) mgr.cleanup();
    }

    public static void cleanupAll() {
        for (ImageTextureManager mgr : INSTANCES.values()) mgr.cleanup();
        INSTANCES.clear();
    }

    private final Identifier textureId;
    private DynamicTexture currentTexture;
    private String currentSource;
    private String lastFailedSource;
    private int originalWidth;
    private int originalHeight;

    private ImageTextureManager(String overlayId) {
        this.textureId = Identifier.fromNamespaceAndPath(
                MiraHUD.MOD_ID, "img_overlay_" + overlayId.replace("-", "_")
        );
    }

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

        String resolved = FilePathUtil.resolve(source);
        Path filePath = Path.of(resolved);
        if (!Files.exists(filePath)) {
            MiraHUD.LOGGER.warn("Overlay image does not exist: {}", source);
            lastFailedSource = source;
            return false;
        }

        cleanup();

        NativeImage image = loadImage(filePath);
        if (image == null) {
            MiraHUD.LOGGER.error("Failed to load overlay image: {}", source);
            lastFailedSource = source;
            return false;
        }

        originalWidth = image.getWidth();
        originalHeight = image.getHeight();
        currentTexture = new DynamicTexture(textureId::toString, image);
        Minecraft.getInstance().getTextureManager().register(textureId, currentTexture);
        currentSource = source;
        lastFailedSource = null;
        return true;
    }

    private NativeImage loadImage(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath)) {
            NativeImage image = NativeImage.read(in);
            if (image != null) return image;
        } catch (IOException ignored) {
            // Unsupported format — fall back to FFmpeg decode below.
        }

        if (!ExternalToolManager.isFfmpegAvailable()) {
            MiraHUD.LOGGER.warn("Image decode fallback needs FFmpeg: {}", filePath.getFileName());
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

            final byte[][] pngRef = new byte[1][];
            Thread drain = new Thread(() -> {
                try (InputStream in = p.getInputStream()) {
                    pngRef[0] = in.readAllBytes();
                } catch (IOException ignored) {
                }
            }, "Image-Decode-" + textureId.getPath());
            drain.setDaemon(true);
            drain.start();

            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                try { drain.join(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                MiraHUD.LOGGER.warn("FFmpeg image decode timed out for {}", filePath);
                return null;
            }
            try { drain.join(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            byte[] pngBytes = pngRef[0];
            if (pngBytes == null || pngBytes.length == 0) {
                MiraHUD.LOGGER.warn("FFmpeg produced no output for image {}", filePath);
                return null;
            }
            try (InputStream in = new ByteArrayInputStream(pngBytes)) {
                return NativeImage.read(in);
            }
        } catch (IOException | InterruptedException e) {
            MiraHUD.LOGGER.error("FFmpeg image decode failed for {}", filePath, e);
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
