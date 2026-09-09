package io.sniperjohnny.github.mirahud.client.screen;

import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonButton;
import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonScreen;
import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import io.sniperjohnny.github.mirahud.client.util.McScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CustomScreen extends NeonScreen {
    public Screen parent;

    public CustomScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new NeonButton(40, 40, 120, 20,
                Component.translatable(TranslationsKeys.SCREEN_HELLO_WORLD_BUTTON), () -> {
                    SystemToast.add(McScreens.getToastManager(this.minecraft), SystemToast.SystemToastId.NARRATOR_TOGGLE,
                            Component.translatable(TranslationsKeys.SCREEN_HELLO_WORLD_TOAST_TITLE),
                            Component.translatable(TranslationsKeys.SCREEN_HELLO_WORLD_TOAST_MESSAGE));
                }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, Component.translatable(TranslationsKeys.SCREEN_SPECIAL_BUTTON), 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        McScreens.setScreen(this.minecraft, this.parent);
    }
}