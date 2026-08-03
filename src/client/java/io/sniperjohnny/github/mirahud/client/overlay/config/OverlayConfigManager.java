package io.sniperjohnny.github.mirahud.client.overlay.config;

import io.sniperjohnny.github.mirahud.MiraHUD;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages multi-overlay config persistence via {@link RootConfig}.
 * Migrates legacy single-overlay configs automatically.
 */
public class OverlayConfigManager {
    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID)
            .resolve("configs")
            .resolve("overlay");

    private static final Path LEGACY_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID + "_overlay.json");

    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("overlays.json");

    /** Old path before overlay configs were moved into the overlay/ subfolder. */
    private static final Path OLD_OVERLAY_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID)
            .resolve("configs")
            .resolve("overlays.json");

    /** Old presets directory before moving into overlay/presets/. */
    private static final Path OLD_PRESETS_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID)
            .resolve("configs")
            .resolve("presets");

    private static final BaseConfigManager<RootConfig> MANAGER =
            new BaseConfigManager<>(RootConfig.class, CONFIG_PATH, RootConfig::new);

    private static boolean migrated;

    public static RootConfig getRootConfig() {
        if (!migrated) migrateIfNeeded();
        return MANAGER.getConfig();
    }

    public static OverlayConfig getConfig() {
        RootConfig root = getRootConfig();
        if (root.overlays.isEmpty()) {
            OverlayConfig fallback = new OverlayConfig();
            root.overlays.add(fallback);
        }
        return root.overlays.get(0);
    }

    public static void load() {
        if (!migrated) migrateIfNeeded();
        MANAGER.load();
    }

    public static void save() {
        if (!migrated) migrateIfNeeded();
        MANAGER.save();
    }

    public static void reload() {
        MANAGER.reload();
        migrated = true;
    }

    public static OverlayConfig copyConfig(OverlayConfig original) {
        OverlayConfig copy = new OverlayConfig();
        copy.id = original.id;
        copy.mediaType = original.mediaType;
        copy.enabled = original.enabled;
        copy.sourcePath = original.sourcePath;
        copy.posX = original.posX;
        copy.posY = original.posY;
        copy.width = original.width;
        copy.height = original.height;
        copy.opacity = original.opacity;
        copy.volume = original.volume;
        copy.anchor = original.anchor;
        copy.lockAspectRatio = original.lockAspectRatio;
        copy.playing = original.playing;
        copy.loop = original.loop;
        copy.options = new java.util.HashMap<>(original.options);
        return copy;
    }

    private static void migrateIfNeeded() {
        migrated = true;

        // 1. Migrate old overlays.json (configs/overlays.json → configs/overlay/overlays.json)
        if (Files.exists(OLD_OVERLAY_PATH) && !Files.exists(CONFIG_PATH)) {
            try {
                Files.createDirectories(CONFIG_DIR);
                Files.move(OLD_OVERLAY_PATH, CONFIG_PATH);
                MiraHUD.LOGGER.info("Migrated overlays.json into overlay/ subfolder.");
            } catch (IOException e) {
                MiraHUD.LOGGER.warn("Failed to migrate overlays.json to new path", e);
            }
        }

        // 2. Migrate old presets dir (configs/presets/ → configs/overlay/presets/)
        if (Files.exists(OLD_PRESETS_DIR) && Files.isDirectory(OLD_PRESETS_DIR)) {
            Path newPresetsDir = CONFIG_DIR.resolve("presets");
            if (!Files.exists(newPresetsDir)) {
                try {
                    Files.createDirectories(CONFIG_DIR);
                    Files.move(OLD_PRESETS_DIR, newPresetsDir);
                    MiraHUD.LOGGER.info("Migrated presets/ into overlay/ subfolder.");
                } catch (IOException e) {
                    MiraHUD.LOGGER.warn("Failed to migrate presets/ to new path", e);
                }
            }
        }

        // 3. Legacy single-overlay config migration
        if (!Files.exists(LEGACY_PATH)) return;
        try {
            String json = Files.readString(LEGACY_PATH);
            OverlayConfig legacy = new com.google.gson.Gson().fromJson(json, OverlayConfig.class);
            if (legacy != null) {
                if (legacy.sourcePath.isEmpty() && !json.contains("sourcePath") && json.contains("imagePath")) {
                    String imagePath = extractLegacyImagePath(json);
                    if (imagePath != null) legacy.sourcePath = imagePath;
                }
                legacy.mediaType = "image";
                if (legacy.id == null || legacy.id.isEmpty()) {
                    legacy.id = java.util.UUID.randomUUID().toString();
                }
                RootConfig root = new RootConfig();
                root.overlays.add(legacy);
                MANAGER.setConfig(root);
                MANAGER.save();
            }
            Files.move(LEGACY_PATH, LEGACY_PATH.resolveSibling(LEGACY_PATH.getFileName() + ".bak"));
        } catch (IOException e) {
            MiraHUD.LOGGER.warn("Failed to migrate legacy overlay config from {}", LEGACY_PATH, e);
        }
    }

    private static String extractLegacyImagePath(String json) {
        int idx = json.indexOf("\"imagePath\"");
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}
