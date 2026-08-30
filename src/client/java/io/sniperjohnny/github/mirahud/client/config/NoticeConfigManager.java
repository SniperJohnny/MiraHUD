package io.sniperjohnny.github.mirahud.client.config;

import io.sniperjohnny.github.mirahud.client.overlay.config.BaseConfigManager;

public final class NoticeConfigManager {

    private static final BaseConfigManager<NoticeConfig> MANAGER =
            new BaseConfigManager<>(NoticeConfig.class, "notice.json", NoticeConfig::new);

    private NoticeConfigManager() {
    }

    public static NoticeConfig getConfig() {
        return MANAGER.getConfig();
    }

    public static void load() {
        MANAGER.load();
    }

    public static void save() {
        MANAGER.save();
    }
}
