package io.sniperjohnny.github.mirahud.client.screen;

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
        /*
        CustomWidget customWidget = new CustomWidget(0, 0, 32, 32, () -> {

        }, "textures/widget_icons/trashcan.png");
        this.addRenderableWidget(customWidget);

         */
        Button buttonWidget = Button.builder(Component.literal("Hello World"), (btn) -> {
            // When the button is clicked, we can display a toast to the screen.
            this.minecraft.getToastManager().addToast(
                    SystemToast.multiline(this.minecraft, SystemToast.SystemToastId.NARRATOR_TOGGLE, Component.nullToEmpty("Hello World!"), Component.nullToEmpty("This is a toast."))
            );
        }).bounds(40, 40, 120, 20).build();
        // x, y, width, height
        // It's recommended to use the fixed height of 20 to prevent rendering issues with the button
        // textures.
        // Register the button widget.
        this.addRenderableWidget(buttonWidget);
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        // Minecraft doesn't have a "label" widget, so we'll have to draw our own text.
        // We'll subtract the font height from the Y position to make the text appear above the button.
        // Subtracting an extra 10 pixels will give the text some padding.
        // textRenderer, text, x, y, color, hasShadow
        graphics.drawString(this.font, "Special Button", 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);

    }
}