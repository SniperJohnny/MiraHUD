package io.sniperjohnny.github.mirahud.client.screen;

import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CustomScreen extends Screen {
    public Screen parent;
    public CustomScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }
    @Override
    protected void init() {
        Button buttonWidget = Button.builder(Component.translatable(TranslationsKeys.SCREEN_HELLO_WORLD_BUTTON), (btn) -> {
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE,
                            Component.translatable(TranslationsKeys.SCREEN_HELLO_WORLD_TOAST_TITLE),
                            Component.translatable(TranslationsKeys.SCREEN_HELLO_WORLD_TOAST_MESSAGE))
            );
        }).bounds(40, 40, 120, 20).build();
        this.addRenderableWidget(buttonWidget);
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawString(this.font, Component.translatable(TranslationsKeys.SCREEN_SPECIAL_BUTTON), 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
