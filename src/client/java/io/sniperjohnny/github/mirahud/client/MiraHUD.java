package io.sniperjohnny.github.mirahud.client;

import io.sniperjohnny.github.mirahud.client.config.NoticeConfig;
import io.sniperjohnny.github.mirahud.client.config.NoticeConfigManager;
import io.sniperjohnny.github.mirahud.client.config.inventoryconfig.InventoryConfigManager;
import io.sniperjohnny.github.mirahud.client.hud_for_client.HudRenderingEntrypoint;
import io.sniperjohnny.github.mirahud.client.overlay.ImageTextureManager;
import io.sniperjohnny.github.mirahud.client.overlay.config.OverlayConfigManager;
import io.sniperjohnny.github.mirahud.client.overlay.config.VideoConfigManager;
import io.sniperjohnny.github.mirahud.client.screen.TranslationNoticeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public class MiraHUD implements ClientModInitializer {

    private static final int NOTICE_WAIT_TICKS = 20;

    private boolean translationNoticeAttempted;
    private Screen stableScreen;
    private int stableScreenTicks;

    private void registerKeyEvents() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ImageTextureManager.cleanupAll();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HudRenderingEntrypoint.stopAllProviders();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ModKeybinds.tick(client);
            showTranslationNoticeOnFirstLaunch(client);
        });
    }

    private void showTranslationNoticeOnFirstLaunch(Minecraft client) {
        if (translationNoticeAttempted) return;
        Screen current = client.screen;
        if (current == null || current != stableScreen) {
            stableScreen = current;
            stableScreenTicks = 0;
            return;
        }
        if (stableScreenTicks++ < NOTICE_WAIT_TICKS) return;
        translationNoticeAttempted = true;
        NoticeConfig config = NoticeConfigManager.getConfig();
        if (!config.translationNoticeShown) {
            config.translationNoticeShown = true;
            NoticeConfigManager.save();
            client.setScreen(new TranslationNoticeScreen(current));
        }
    }

    @Override
    public void onInitializeClient() {
        NoticeConfigManager.load();
        VideoConfigManager.load();
        OverlayConfigManager.load();
        ModKeybinds.register();
        InventoryConfigManager.load();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(io.sniperjohnny.github.mirahud.MiraHUD.MOD_ID, "before_chat"),
                HudRenderingEntrypoint::render
        );
        registerKeyEvents();
    }
}
