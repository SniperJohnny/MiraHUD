package io.sniperjohnny.github.mirage.client.hud_for_client;

import io.sniperjohnny.github.mirage.client.overlay.ImageTextureManager;
import io.sniperjohnny.github.mirage.client.overlay.MediaProvider;
import io.sniperjohnny.github.mirage.client.overlay.VideoTextureManager;
import io.sniperjohnny.github.mirage.client.overlay.config.OverlayConfig;
import io.sniperjohnny.github.mirage.client.overlay.config.RootConfig;
import io.sniperjohnny.github.mirage.client.overlay.config.OverlayConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HudRenderingEntrypoint {

    /** Maps overlay id → its active media provider. */
    private static final Map<String, MediaProvider> providers = new ConcurrentHashMap<>();
    /** Tracks last-failed sources per overlay (value = failure timestamp in ms). */
    private static final Map<String, Long> failedSources = new ConcurrentHashMap<>();
    /** How long to wait before retrying a failed source (transient failures recover). */
    private static final long FAILED_RETRY_INTERVAL_MS = 5000;

    /** Minimum time between restarts of a crashed video pipeline. */
    private static final long VIDEO_RESTART_COOLDOWN_MS = 3000;
    /** Cap on the backoff cooldown for a repeatedly-crashing video. */
    private static final long VIDEO_RESTART_MAX_COOLDOWN_MS = 60_000;
    /** Tracks last restart attempt per overlay id to avoid restart storms. */
    private static final Map<String, Long> lastVideoRestart = new ConcurrentHashMap<>();
    /** Consecutive crash count per overlay id (drives exponential backoff). */
    private static final Map<String, Integer> videoRestartCount = new ConcurrentHashMap<>();
    /** When the current pipeline was first seen alive, per overlay id. */
    private static final Map<String, Long> videoAliveSince = new ConcurrentHashMap<>();

    public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        RootConfig root = OverlayConfigManager.getRootConfig();
        if (root.overlays.isEmpty()) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        for (OverlayConfig cfg : root.overlays) {
            if (!cfg.enabled) {
                // Keep disabled overlays silent: pause the provider (and its
                // audio) instead of letting FFmpeg keep playing invisibly in
                // the background. Re-enabling resumes via playPause(cfg.playing)
                // in getOrCreateProvider. Guarded by isPlaying() so this only
                // fires on the disable transition, not every frame.
                MediaProvider hidden = providers.get(cfg.id);
                if (hidden != null && hidden.isVideo() && hidden.isPlaying()) {
                    hidden.playPause(false);
                }
                continue;
            }
            if (cfg.sourcePath.isBlank()) continue;

            // Skip known-failed sources to avoid retrying every frame, but allow
            // periodic retries so transient failures (locked file, FFmpeg just
            // installed, etc.) self-recover.
            Long lastFail = failedSources.get(cfg.id);
            if (lastFail != null && System.currentTimeMillis() - lastFail < FAILED_RETRY_INTERVAL_MS) {
                continue;
            }

            MediaProvider provider = getOrCreateProvider(cfg);
            if (provider == null) continue;

            // Tick video providers BEFORE hasTexture check — tick() creates the texture
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
                // Self-heal: if the FFmpeg pipeline crashed/EOF'd, restart it
                // (throttled with exponential backoff so a permanently-crashing
                // file doesn't spawn FFmpeg every frame — or every 3s forever).
                if (existing.isAlive()) {
                    long now = System.currentTimeMillis();
                    Long aliveSince = videoAliveSince.get(cfg.id);
                    if (aliveSince == null) {
                        videoAliveSince.put(cfg.id, now);
                    } else if (now - aliveSince >= VIDEO_RESTART_MAX_COOLDOWN_MS) {
                        // Sustained-alive — healthy pipeline, reset crash backoff.
                        videoRestartCount.remove(cfg.id);
                    }
                } else {
                    videoAliveSince.remove(cfg.id);
                    // Loop: seamless in-place restart — the provider keeps its
                    // GPU texture and buffers (see VideoTextureManager.seek),
                    // so the overlay doesn't flash and the GPU isn't churned
                    // every loop iteration. No backoff: a natural loop restart
                    // is expected, not a crash.
                    if (cfg.loop) {
                        existing.restart();
                        if (existing.isAlive()) return existing;
                        // Restart failed (e.g. ffprobe error) — drop the
                        // provider so the failed-source throttling path retries
                        // later instead of hot-looping every frame.
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

        // Source changed — cleanup old provider
        if (existing != null) {
            existing.cleanup();
            providers.remove(cfg.id);
        }
        failedSources.remove(cfg.id); // clear stale failure for a new attempt
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
        videoAliveSince.remove(cfg.id); // fresh pipeline — will be re-seeded when seen alive
        // NOTE: do NOT reset videoRestartCount here — updateSource succeeding only
        // means FFmpeg spawned, not that it survived. Resetting on spawn would
        // defeat the exponential backoff for files that start then instantly die.
        // The count is reset only after the pipeline proves sustained-alive.
        providers.put(cfg.id, provider);
        return provider;
    }

    /**
     * True if enough time has passed since the last restart attempt.
     * Cooldown doubles per consecutive crash (3s, 6s, 12s, … capped at 60s).
     */
    private static boolean canRestartVideo(String id) {
        long now = System.currentTimeMillis();
        int crashes = videoRestartCount.getOrDefault(id, 0);
        long cooldown = Math.min(
                VIDEO_RESTART_COOLDOWN_MS * (1L << Math.min(crashes, 5)),
                VIDEO_RESTART_MAX_COOLDOWN_MS);
        Long last = lastVideoRestart.get(id);
        if (last == null || now - last >= cooldown) {
            lastVideoRestart.put(id, now);
            videoRestartCount.put(id, crashes + 1);
            return true;
        }
        return false;
    }

    /** Call when config screen closes to sync and clean up providers. */
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

    /** Stop ALL providers (called on world disconnect to prevent audio leaks). */
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

    /**
     * Toggle play/pause on all active video providers and update the config.
     * @return the new playing state (true = now playing, false = now paused)
     */
    public static boolean togglePlayPauseAllVideos() {
        RootConfig root = OverlayConfigManager.getRootConfig();
        // Determine current state from the first active video provider
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

    /**
     * Seek all active video providers by a relative amount of seconds.
     * Positive = forward, negative = backward.
     * @return the new playback position in seconds (from the first video found)
     */
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

    /** Restart all active video providers from the beginning. */
    public static void restartAllVideos() {
        for (MediaProvider p : providers.values()) {
            if (p.isVideo()) {
                p.restart();
            }
        }
        // Update config: set playing=true for all video overlays after restart
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
