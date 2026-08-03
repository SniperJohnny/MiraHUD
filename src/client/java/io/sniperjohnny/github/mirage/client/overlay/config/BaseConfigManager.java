package io.sniperjohnny.github.mirage.client.overlay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.sniperjohnny.github.mirage.Mirage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Generic JSON config manager that any part of the mod can use to persist settings.
 * <p>
 * Usage:
 * <pre>{@code
 *   BaseConfigManager<MyConfig> manager = new BaseConfigManager<>(
 *       MyConfig.class,
 *       "my_config.json",
 *       MyConfig::new
 *   );
 *   MyConfig cfg = manager.getConfig();
 *   cfg.someField = 42;
 *   manager.save();
 * }</pre>
 *
 * @param <T> the config POJO type (must have a no-arg constructor for Gson)
 */
public class BaseConfigManager<T> {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Class<T> configClass;
    private final Path configPath;
    private final Supplier<T> defaultSupplier;
    private T config;

    /**
     * @param configClass     the class object for deserialization
     * @param configFileName  file name placed inside {@code config/<modid>/configs/}
     * @param defaultSupplier factory for a default config instance
     */
    public BaseConfigManager(Class<T> configClass, String configFileName, Supplier<T> defaultSupplier) {
        this.configClass = configClass;
        this.configPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Mirage.MOD_ID)
                .resolve("configs")
                .resolve(configFileName);
        this.defaultSupplier = defaultSupplier;
    }

    /**
     * Alternate constructor accepting an explicit config path for backward compatibility.
     */
    public BaseConfigManager(Class<T> configClass, Path configPath, Supplier<T> defaultSupplier) {
        this.configClass = configClass;
        this.configPath = configPath;
        this.defaultSupplier = defaultSupplier;
    }

    public T getConfig() {
        if (config == null) {
            load();
        }
        return config;
    }

    public void load() {
        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = GSON.fromJson(json, configClass);
            } catch (Exception e) {
                Mirage.LOGGER.warn("Failed to read config {}, using defaults.", configPath.getFileName(), e);
            }
        }
        if (config == null) {
            config = defaultSupplier.get();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config));
        } catch (IOException e) {
            Mirage.LOGGER.error("Failed to save config {}.", configPath.getFileName(), e);
        }
    }

    /** Forces a reload from disk, discarding unsaved changes. */
    public void reload() {
        config = null;
        load();
    }

    /** Replace the in-memory config (e.g. restoring from a preset). */
    public void setConfig(T newConfig) {
        this.config = newConfig;
    }
}
