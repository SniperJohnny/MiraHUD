package io.sniperjohnny.github.mirage.client.overlay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.sniperjohnny.github.mirage.Mirage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OverlayPresetManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PRESETS_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(Mirage.MOD_ID)
            .resolve("configs")
            .resolve("overlay")
            .resolve("presets");

    private static List<OverlayPreset> presets = new ArrayList<>();

    public static void load() {
        presets.clear();
        if (!Files.exists(PRESETS_DIR)) {
            return;
        }

        try (Stream<Path> stream = Files.list(PRESETS_DIR)) {
            stream.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String json = Files.readString(path);
                            OverlayPreset preset = GSON.fromJson(json, OverlayPreset.class);
                            if (preset.getName() == null || preset.getName().isBlank()) {
                                preset.setName(presetFileName(path));
                            }
                            presets.add(preset);
                        } catch (Exception e) {
                            Mirage.LOGGER.warn("Failed to read overlay preset: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            Mirage.LOGGER.error("Failed to list overlay presets.", e);
        }

        presets.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
    }

    public static List<String> getPresetNames() {
        return Collections.unmodifiableList(presets.stream().map(OverlayPreset::getName).collect(Collectors.toList()));
    }

    public static OverlayPreset getPreset(String name) {
        return presets.stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public static boolean savePreset(String name, OverlayConfig config) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String sanitized = sanitizeName(name);
        if (sanitized.isEmpty()) {
            return false;
        }

        OverlayPreset preset = new OverlayPreset(name, OverlayConfigManager.copyConfig(config));
        Path path = PRESETS_DIR.resolve(sanitized + ".json");
        try {
            Files.createDirectories(PRESETS_DIR);
            Files.writeString(path, GSON.toJson(preset));
        } catch (IOException e) {
            Mirage.LOGGER.error("Failed to save overlay preset: {}", name, e);
            return false;
        }

        load();
        return true;
    }

    public static boolean deletePreset(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String sanitized = sanitizeName(name);
        Path path = PRESETS_DIR.resolve(sanitized + ".json");
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Mirage.LOGGER.error("Failed to delete overlay preset: {}", name, e);
            return false;
        }

        load();
        return true;
    }

    private static String sanitizeName(String name) {
        return name.trim().replaceAll("[^a-zA-Z0-9\\\\-_ ]", "").replaceAll("\\\\s+", "_");
    }

    private static String presetFileName(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".json")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }

}
