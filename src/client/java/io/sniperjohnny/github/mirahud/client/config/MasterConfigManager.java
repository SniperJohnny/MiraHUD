package io.sniperjohnny.github.mirahud.client.config;

import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.overlay.config.BaseConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Singleton manager for the {@link MasterConfig}.
 * Loads from {@code config/mirahud/configs/master.json}.
 */
public class MasterConfigManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID)
            .resolve("configs")
            .resolve("master.json");

    private static final BaseConfigManager<MasterConfig> MANAGER =
            new BaseConfigManager<>(MasterConfig.class, CONFIG_PATH, MasterConfig::new);

    public static MasterConfig getConfig() { return MANAGER.getConfig(); }

    public static void load() { MANAGER.load(); }

    public static void save() { MANAGER.save(); }

    public static void reload() { MANAGER.reload(); }
}
