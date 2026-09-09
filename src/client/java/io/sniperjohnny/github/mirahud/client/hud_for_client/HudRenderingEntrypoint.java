package io.sniperjohnny.github.mirahud.client.hud_for_client;

import io.sniperjohnny.github.mirahud.client.overlay.ImageTextureManager;
import io.sniperjohnny.github.mirahud.client.overlay.MediaProvider;
import io.sniperjohnny.github.mirahud.client.overlay.VideoTextureManager;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfig;
import io.sniperjohnny.github.mirahud.client.overlay.config.RootConfig;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HudRenderingEntrypoint {

    private static final Map<String, MediaProvider> providers = new ConcurrentHashMap<>();
    private static final Map<String, Long> failedSources = new ConcurrentHashMap<>();
    private static final long FAILED_RETRY_INTERVAL_MS = 5000;

    private static final long VIDEO_RESTART_COOLDOWN_MS = 3000;
    private static final long VIDEO_RESTART_MAX_COOLDOWN_MS = 60_000;
    private static final int VIDEO_RESTART_MAX_BACKOFF_EXPONENT = 5;
    private static final Map<String, Long> lastVideoRestart = new ConcurrentHashMap<>();
    private static final Map<String, Integer> videoRestartCount = new ConcurrentHashMap<>();
    private static final Map<String, Long> videoAliveSince = new ConcurrentHashMap<>();

    public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        RootConfig root = OverlayConfigManager.getRootConfig();
        if (root.overlays.isEmpty()) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        for (OverlayConfig cfg : root.overlays) {
            if (!cfg.enabled) {
                MediaProvider hidden = providers.get(cfg.id);
                if (hidden != null && hidden.isVideo() && hidden.isPlaying()) {
                    hidden.playPause(false);
                }
                continue;
            }
            if (cfg.sourcePath.isBlank()) continue;

            Long lastFail = failedSources.get(cfg.id);
            if (lastFail != null && System.currentTimeMillis() - lastFail < FAILED_RETRY_INTERVAL_MS) {
                continue;
            }

            MediaProvider provider = getOrCreateProvider(cfg);
            if (provider == null) continue;

            if (provider.isVideo()) {
                provider.tick();
            }

            if (!provider.hasTexture()) continue;

            int x = calculateX(cfg, screenWidth);
            int y = calculateY(cfg, screenHeight);

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    provider.getTextureId(),
                    x, y,
                    0, 0,
                    cfg.width, cfg.height,
                    cfg.width, cfg.height,
                    cfg.getColor()
            );
        }
    }

    private static MediaProvider getOrCreateProvider(OverlayConfig cfg) {
        MediaProvider existing = providers.get(cfg.id);
        if (existing != null && cfg.sourcePath.equals(existing.getCurrentSource())) {
            if (existing.isVideo()) {
                existing.playPause(cfg.playing);
                existing.setVolume(cfg.volume);
                if (existing instanceof VideoTextureManager vtm) {
                    vtm.setLoop(cfg.loop);
                }
                if (existing.isAlive()) {
                    long now = System.currentTimeMillis();
                    Long aliveSince = videoAliveSince.get(cfg.id);
                    if (aliveSince == null) {
                        videoAliveSince.put(cfg.id, now);
                    } else if (now - aliveSince >= VIDEO_RESTART_MAX_COOLDOWN_MS) {
                        videoRestartCount.remove(cfg.id);
                    }
                } else {
                    videoAliveSince.remove(cfg.id);
                    if (cfg.loop) {
                        existing.restart();
                        if (existing.isAlive()) return existing;
                        existing.cleanup();
                        providers.remove(cfg.id);
                        return null;
                    }
                    if (canRestartVideo(cfg.id)) {
                        existing.cleanup();
                        providers.remove(cfg.id);
                        return createProvider(cfg);
                    }
                }
            }
            return existing;
        }

        if (existing != null) {
            existing.cleanup();
            providers.remove(cfg.id);
        }
        failedSources.remove(cfg.id);
        return createProvider(cfg);
    }

    private static MediaProvider createProvider(OverlayConfig cfg) {
        MediaProvider provider;
        if (cfg.isVideo()) {
            provider = new VideoTextureManager(cfg.id);
        } else {
            provider = ImageTextureManager.forOverlay(cfg.id);
        }

        boolean ok = provider.updateSource(cfg.sourcePath);
        if (!ok) {
            failedSources.put(cfg.id, System.currentTimeMillis());
            provider.cleanup();
            if (!cfg.isVideo()) ImageTextureManager.removeOverlay(cfg.id);
            return null;
        }

        failedSources.remove(cfg.id);
        videoAliveSince.remove(cfg.id);
        // Keep videoRestartCount: a spawn that later crashes must still count towards backoff.
        providers.put(cfg.id, provider);
        return provider;
    }

    private static boolean canRestartVideo(String id) {
        long now = System.currentTimeMillis();
        int crashes = videoRestartCount.getOrDefault(id, 0);
        long cooldown = Math.min(
                VIDEO_RESTART_COOLDOWN_MS * (1L << Math.min(crashes, VIDEO_RESTART_MAX_BACKOFF_EXPONENT)),
                VIDEO_RESTART_MAX_COOLDOWN_MS);
        Long last = lastVideoRestart.get(id);
        if (last == null || now - last >= cooldown) {
            lastVideoRestart.put(id, now);
            videoRestartCount.put(id, crashes + 1);
            return true;
        }
        return false;
    }

    public static MediaProvider getProvider(String overlayId) {
        return providers.get(overlayId);
    }

    public static void syncProviders() {
        RootConfig root = OverlayConfigManager.getRootConfig();
        Iterator<Map.Entry<String, MediaProvider>> it = providers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, MediaProvider> entry = it.next();
            if (root.findById(entry.getKey()) == null) {
                entry.getValue().cleanup();
                ImageTextureManager.removeOverlay(entry.getKey());
                failedSources.remove(entry.getKey());
                lastVideoRestart.remove(entry.getKey());
                videoRestartCount.remove(entry.getKey());
                videoAliveSince.remove(entry.getKey());
                it.remove();
            }
        }
    }

    public static void stopAllProviders() {
        for (Map.Entry<String, MediaProvider> entry : providers.entrySet()) {
            entry.getValue().cleanup();
            ImageTextureManager.removeOverlay(entry.getKey());
        }
        providers.clear();
        failedSources.clear();
        lastVideoRestart.clear();
        videoRestartCount.clear();
        videoAliveSince.clear();
    }

    public static boolean togglePlayPauseAllVideos() {
        RootConfig root = OverlayConfigManager.getRootConfig();
        boolean anyPlaying = false;
        for (MediaProvider p : providers.values()) {
            if (p.isVideo() && p.isAlive()) {
                anyPlaying = p.isPlaying();
                break;
            }
        }
        boolean newState = !anyPlaying;
        for (OverlayConfig cfg : root.overlays) {
            if (cfg.isVideo() && cfg.enabled) {
                cfg.playing = newState;
                MediaProvider p = providers.get(cfg.id);
                if (p != null) p.playPause(newState);
            }
        }
        OverlayConfigManager.save();
        return newState;
    }

    public static double seekAllVideos(double deltaSeconds) {
        double newPos = 0;
        boolean found = false;
        for (MediaProvider p : providers.values()) {
            if (p.isVideo() && p.isAlive()) {
                double current = p.getPlaybackPositionSeconds();
                double target = Math.max(0, current + deltaSeconds);
                if (!found) {
                    newPos = target;
                    found = true;
                }
                p.seek(target);
            }
        }
        return newPos;
    }

    public static void restartAllVideos() {
        for (MediaProvider p : providers.values()) {
            if (p.isVideo()) {
                p.restart();
            }
        }
        RootConfig root = OverlayConfigManager.getRootConfig();
        for (OverlayConfig cfg : root.overlays) {
            if (cfg.isVideo() && cfg.enabled) {
                cfg.playing = true;
            }
        }
        OverlayConfigManager.save();
    }

    public static int calculateX(OverlayConfig config, int screenWidth) {
        int x = config.posX;
        switch (config.anchor) {
            case TOP_RIGHT: case BOTTOM_RIGHT:
                x = screenWidth - config.width - config.posX; break;
            case CENTER:
                x = (screenWidth - config.width) / 2 + config.posX; break;
            default: break;
        }
        return x;
    }

    public static int calculateY(OverlayConfig config, int screenHeight) {
        int y = config.posY;
        switch (config.anchor) {
            case BOTTOM_LEFT: case BOTTOM_RIGHT:
                y = screenHeight - config.height - config.posY; break;
            case CENTER:
                y = (screenHeight - config.height) / 2 + config.posY; break;
            default: break;
        }
        return y;
    }
}
