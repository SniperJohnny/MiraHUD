package io.sniperjohnny.github.mirage.client.overlay.config;

import io.sniperjohnny.github.mirage.client.config.MasterConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Root configuration that persists all overlays in a single JSON file.
 * Wraps a list of {@link OverlayConfig} for multi-overlay support.
 * <p>
 * Global player preferences live in {@link MasterConfig} — this class
 * is overlay-specific only.
 */
public class RootConfig {
    public List<OverlayConfig> overlays = new ArrayList<>();

    public RootConfig() {
        // Gson needs a no-arg constructor
    }

    /** Ensures at least one overlay exists, creating a default one if needed. */
    public void ensureAtLeastOne() {
        if (overlays.isEmpty()) {
            overlays.add(new OverlayConfig());
        }
    }

    /** Find an overlay by its unique id. */
    public OverlayConfig findById(String id) {
        for (OverlayConfig cfg : overlays) {
            if (cfg.id.equals(id)) return cfg;
        }
        return null;
    }
}
