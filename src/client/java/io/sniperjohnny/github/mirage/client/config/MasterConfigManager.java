package io.sniperjohnny.github.mirage.client.config;

import io.sniperjohnny.github.mirage.Mirage;
import io.sniperjohnny.github.mirage.client.overlay.config.BaseConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Singleton manager for the {@link MasterConfig}.
 * Loads from {@code config/mirage/configs/master.json}.
 */
public class MasterConfigManager {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(Mirage.MOD_ID)
            .resolve("configs")
            .resolve("master.json");

    private static final BaseConfigManager<MasterConfig> MANAGER =
            new BaseConfigManager<>(MasterConfig.class, CONFIG_PATH, MasterConfig::new);

    public static MasterConfig getConfig() { return MANAGER.getConfig(); }

    public static void load() { MANAGER.load(); }

    public static void save() { MANAGER.save(); }

    public static void reload() { MANAGER.reload(); }
}
