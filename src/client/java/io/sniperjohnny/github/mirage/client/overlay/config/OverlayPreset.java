package io.sniperjohnny.github.mirage.client.overlay.config;

public class OverlayPreset {
    private String name;
    private OverlayConfig config;

    public OverlayPreset() {
    }

    public OverlayPreset(String name, OverlayConfig config) {
        this.name = name;
        this.config = config;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OverlayConfig getConfig() {
        return config;
    }

    public void setConfig(OverlayConfig config) {
        this.config = config;
    }
}
