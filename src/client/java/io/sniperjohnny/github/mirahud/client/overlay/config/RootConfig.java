package io.sniperjohnny.github.mirahud.client.overlay.config;

import java.util.ArrayList;
import java.util.List;

public class RootConfig {
    public List<OverlayConfig> overlays = new ArrayList<>();

    public RootConfig() {
    }

    public void ensureAtLeastOne() {
        if (overlays.isEmpty()) {
            overlays.add(new OverlayConfig());
        }
    }

    public OverlayConfig findById(String id) {
        for (OverlayConfig cfg : overlays) {
            if (cfg.id.equals(id)) return cfg;
        }
        return null;
    }
}
