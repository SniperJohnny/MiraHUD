package io.sniperjohnny.github.mirahud.client.hud_for_client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class NeonSlider extends AbstractSliderButton {

    private static final float LERP = 0.15f;
    private static final int HANDLE_WIDTH = 4;

    private int currentColor = NeonScreen.IDLE;
    private int currentGlow = 0;

    public NeonSlider(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();
        boolean engaged = this.value > 0 || this.isFocused();
        int target = !this.active ? NeonScreen.DISABLED
                : hovered ? NeonScreen.HOVER
                : engaged ? NeonScreen.ACTIVE : NeonScreen.IDLE;
        this.currentColor = NeonScreen.lerpColor(this.currentColor, target, LERP);
        this.currentGlow = (int) (this.currentGlow + (NeonScreen.glowFor(hovered, engaged) - this.currentGlow) * LERP);

        int handleX = this.getX() + (int) (this.value * (this.width - HANDLE_WIDTH));
        int midY = this.getY() + this.height / 2;
        graphics.fill(this.getX() + 1, midY - 1, this.getX() + this.width - 1, midY + 1, this.currentColor);
        graphics.fill(handleX, this.getY() + 2, handleX + HANDLE_WIDTH, this.getY() + this.height - 2, this.currentColor);
        graphics.renderOutline(handleX - 2, this.getY(), HANDLE_WIDTH + 4, this.height, this.currentGlow);
    }
}
