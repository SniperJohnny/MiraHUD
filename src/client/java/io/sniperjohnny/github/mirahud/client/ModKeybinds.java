package io.sniperjohnny.github.mirahud.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.hud_for_client.HudRenderingEntrypoint;
import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfig;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfigManager;
import io.sniperjohnny.github.mirahud.client.overlay.config.RootConfig;
import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonOverlayConfigScreen;
import io.sniperjohnny.github.mirahud.client.screen.InventoryWidgetManagerScreen;
import io.sniperjohnny.github.mirahud.client.util.McScreens;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class ModKeybinds {
    private static KeyMapping pauseVideo;
    private static KeyMapping openOverlayConfigKey;
    private static KeyMapping toggleOverlayKey;
    private static KeyMapping toggleInventoryConfigOverlayKey;
    private static KeyMapping skipForwardKey;
    private static KeyMapping skipBackwardKey;
    private static KeyMapping restartVideoKey;
    private static volatile boolean registered;

    private ModKeybinds() {}

    public static void register() {
        if (registered) return;
        registered = true;

        KeyMapping.Category CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MiraHUD.MOD_ID, "mirahud_keybinds")
        );

        pauseVideo = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        TranslationsKeys.KEY_PAUSE_VIDEO,
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_P,
                        CATEGORY
                )
        );
        toggleInventoryConfigOverlayKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        TranslationsKeys.KEY_OPEN_INVENTORY_CONFIG,
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_I,
                        CATEGORY
                )
        );
        openOverlayConfigKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        TranslationsKeys.KEY_OPEN_OVERLAY_CONFIG,
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_O,
                        CATEGORY
                )
        );

        toggleOverlayKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        TranslationsKeys.KEY_TOGGLE_OVERLAY,
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_K,
                        CATEGORY
                )
        );

        skipForwardKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        TranslationsKeys.KEY_SKIP_FORWARD,
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_RIGHT,
                        CATEGORY
                )
        );

        skipBackwardKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        TranslationsKeys.KEY_SKIP_BACKWARD,
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_LEFT,
                        CATEGORY
                )
        );

        restartVideoKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(
                        TranslationsKeys.KEY_RESTART_VIDEO,
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_R,
                        CATEGORY
                )
        );

        MiraHUD.LOGGER.info("ModKeybinds registered (pause=P, skip=L/R arrows, restart=R)");
    }

    public static void tick(Minecraft client) {
        if (pauseVideo == null) return;
        while (openOverlayConfigKey.consumeClick()) {
            Screen currentScreen = McScreens.getScreen(Minecraft.getInstance());
            McScreens.setScreen(Minecraft.getInstance(), new NeonOverlayConfigScreen(currentScreen));
        }

        while (toggleOverlayKey.consumeClick()) {
            RootConfig root = OverlayConfigManager.getRootConfig();
            if (root.overlays.isEmpty()) return;
            OverlayConfig first = root.overlays.get(0);
            boolean newState = !first.enabled;
            for (OverlayConfig cfg : root.overlays) {
                cfg.enabled = newState;
            }
            OverlayConfigManager.save();
            if (client.player != null) {
                client.player.sendSystemMessage(
                        Component.translatable(TranslationsKeys.MESSAGE_OVERLAY_TOGGLED,
                                Component.translatable(newState ? "options.on" : "options.off"))
                );
            }
        }
        while (pauseVideo.consumeClick()) {
            boolean nowPlaying = HudRenderingEntrypoint.togglePlayPauseAllVideos();
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable(nowPlaying
                                ? TranslationsKeys.MESSAGE_VIDEO_RESUMED
                                : TranslationsKeys.MESSAGE_VIDEO_PAUSED)
                );
            }
        }
        while (toggleInventoryConfigOverlayKey.consumeClick()) {
            Screen currentScreen = McScreens.getScreen(Minecraft.getInstance());
            McScreens.setScreen(Minecraft.getInstance(), new InventoryWidgetManagerScreen(
                    Component.translatable(TranslationsKeys.SCREEN_INVENTORY_WIDGET_MANAGER), currentScreen));
        }
        while (skipForwardKey.consumeClick()) {
            double newPos = HudRenderingEntrypoint.seekAllVideos(10);
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable(TranslationsKeys.MESSAGE_SKIPPED_FORWARD, String.format("%.1f", newPos))
                );
            }
        }
        while (skipBackwardKey.consumeClick()) {
            double newPos = HudRenderingEntrypoint.seekAllVideos(-10);
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable(TranslationsKeys.MESSAGE_SKIPPED_BACKWARD, String.format("%.1f", newPos))
                );
            }
        }
        while (restartVideoKey.consumeClick()) {
            HudRenderingEntrypoint.restartAllVideos();
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable(TranslationsKeys.MESSAGE_VIDEO_RESTARTED)
                );
            }
        }
    }
}
