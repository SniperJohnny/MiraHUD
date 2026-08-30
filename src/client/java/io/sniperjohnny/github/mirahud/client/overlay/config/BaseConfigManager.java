package io.sniperjohnny.github.mirahud.client.overlay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.sniperjohnny.github.mirahud.MiraHUD;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class BaseConfigManager<T> {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private final Class<T> configClass;
    private final Path configPath;
    private final Supplier<T> defaultSupplier;
    private T config;

    public BaseConfigManager(Class<T> configClass, String configFileName, Supplier<T> defaultSupplier) {
        this.configClass = configClass;
        this.configPath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve(MiraHUD.MOD_ID)
                .resolve("configs")
                .resolve(configFileName);
        this.defaultSupplier = defaultSupplier;
    }

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
                MiraHUD.LOGGER.warn("Failed to read config {}, using defaults.", configPath.getFileName(), e);
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
            MiraHUD.LOGGER.error("Failed to save config {}.", configPath.getFileName(), e);
        }
    }

    public void reload() {
        config = null;
        load();
    }

    public void setConfig(T newConfig) {
        this.config = newConfig;
    }
}
