package io.sniperjohnny.github.mirahud.client.overlay.config;

public final class VideoConfigManager {

    private static final BaseConfigManager<VideoConfig> MANAGER =
            new BaseConfigManager<>(VideoConfig.class, "video.json", VideoConfig::new);

    private VideoConfigManager() {
    }

    public static VideoConfig getConfig() {
        return MANAGER.getConfig();
    }

    public static void load() {
        MANAGER.load();
    }

    public static void save() {
        MANAGER.save();
    }
}
