package io.sniperjohnny.github.mirahud.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.hud_for_client.HudRenderingEntrypoint;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfig;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfigManager;
import io.sniperjohnny.github.mirahud.client.overlay.config.RootConfig;
import io.sniperjohnny.github.mirahud.client.screen.InventoryWidgetManagerScreen;
import io.sniperjohnny.github.mirahud.client.screen.OverlayConfigScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Central registry for all mod keybinds. Call {@link #register()} during
 * client init and {@link #tick(Minecraft)} every client tick.
 */
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

    /** Register keybinds. Must be called from {@code onInitializeClient()}. */
    public static void register() {
        if (registered) return;
        registered = true;

        KeyMapping.Category CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MiraHUD.MOD_ID, "mirahud_keybinds")
        );

        pauseVideo = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.mirahud.pause_video",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_P,
                        CATEGORY
                )
        );
        toggleInventoryConfigOverlayKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.mirahud.open_inventory_config",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_I,
                        CATEGORY
                )
        );
        openOverlayConfigKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.mirahud.open_overlay_config",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_O,
                        CATEGORY
                )
        );

        toggleOverlayKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.mirahud.toggle_overlay",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_K,
                        CATEGORY
                )
        );

        skipForwardKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.mirahud.skip_forward",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_RIGHT,
                        CATEGORY
                )
        );

        skipBackwardKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.mirahud.skip_backward",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_LEFT,
                        CATEGORY
                )
        );

        restartVideoKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.mirahud.restart_video",
                        InputConstants.Type.KEYSYM,
                        InputConstants.KEY_R,
                        CATEGORY
                )
        );

        MiraHUD.LOGGER.info("ModKeybinds registered (pause=P, skip=L/R arrows, restart=R)");
    }

    /** Call from {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void tick(Minecraft client) {
        if (pauseVideo == null) return;
        while (openOverlayConfigKey.consumeClick()) {
            Screen currentScreen = Minecraft.getInstance().screen;
            Minecraft.getInstance().setScreen(new OverlayConfigScreen(currentScreen));
        }

        while (toggleOverlayKey.consumeClick()) {
            RootConfig root = OverlayConfigManager.getRootConfig();
            if (root.overlays.isEmpty()) return;
            // Toggle all overlays
            OverlayConfig first = root.overlays.get(0);
            boolean newState = !first.enabled;
            for (OverlayConfig cfg : root.overlays) {
                cfg.enabled = newState;
            }
            OverlayConfigManager.save();
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.translatable("message.mirahud.overlay_toggled",
                                Component.translatable(newState ? "options.on" : "options.off")),
                        false
                );
            }
        }
        while (pauseVideo.consumeClick()) {
            boolean nowPlaying = HudRenderingEntrypoint.togglePlayPauseAllVideos();
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.translatable(nowPlaying
                                ? "message.mirahud.video_resumed"
                                : "message.mirahud.video_paused"),
                        true
                );
            }
        }
        while (toggleInventoryConfigOverlayKey.consumeClick()) {
            Screen currentScreen = Minecraft.getInstance().screen;
            Minecraft.getInstance().setScreen(new InventoryWidgetManagerScreen(Component.empty(), currentScreen));
        }
        while (skipForwardKey.consumeClick()) {
            double newPos = HudRenderingEntrypoint.seekAllVideos(10);
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("Skipped forward (+10s)  " + String.format("%.1f", newPos) + "s"),
                        true
                );
            }
        }
        while (skipBackwardKey.consumeClick()) {
            double newPos = HudRenderingEntrypoint.seekAllVideos(-10);
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("Skipped backward (-10s)  " + String.format("%.1f", newPos) + "s"),
                        true
                );
            }
        }
        while (restartVideoKey.consumeClick()) {
            HudRenderingEntrypoint.restartAllVideos();
            if (client.player != null) {
                client.player.displayClientMessage(
                        Component.literal("Video restarted"),
                        true
                );
            }
        }
    }
}
