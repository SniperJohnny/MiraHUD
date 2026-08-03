package io.sniperjohnny.github.mirage.client;

import io.sniperjohnny.github.mirage.client.config.inventoryconfig.InventoryConfigManager;
import io.sniperjohnny.github.mirage.client.hud_for_client.HudRenderingEntrypoint;
import io.sniperjohnny.github.mirage.client.overlay.ImageTextureManager;
import io.sniperjohnny.github.mirage.client.config.MasterConfigManager;
import io.sniperjohnny.github.mirage.client.overlay.config.OverlayConfigManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class Mirage implements ClientModInitializer {

/*
    KeyMapping sendToChatKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.categories.mirage.mirage_keybinds",
                    InputConstants.Type.KEYSYM,
                    InputConstants.KEY_J,
                    CATEGORY
            )
    );

 */



    private void registerKeyEvents() {
        // Cleanup textures on client stop
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ImageTextureManager.cleanupAll();
        });

        // Stop all video/audio providers when disconnecting from a world
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HudRenderingEntrypoint.stopAllProviders();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            /*
            while (sendToChatKey.consumeClick()) {
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("Key Pressed!"), false);
                    Screen currentScreen = Minecraft.getInstance().screen;
                    Minecraft.getInstance().setScreen(
                            new CustomScreen(Component.empty(), currentScreen)
                    );
                }
            }


             */


            ModKeybinds.tick(client);
        });
    }

    @Override
    public void onInitializeClient() {
        MasterConfigManager.load();
        OverlayConfigManager.load();
        ModKeybinds.register();
        InventoryConfigManager.load();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(io.sniperjohnny.github.mirage.Mirage.MOD_ID, "before_chat"),
                HudRenderingEntrypoint::render
        );
        registerKeyEvents();
    }
}
