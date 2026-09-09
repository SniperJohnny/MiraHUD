package io.sniperjohnny.github.mirahud.client.hud_for_client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class NeonSlider extends AbstractSliderButton {

    private static final int HANDLE_WIDTH = 6;

    private int currentColor = NeonScreen.IDLE;
    private int currentGlow = 0;
    private long lastNanos = System.nanoTime();
    private boolean initialized;

    public NeonSlider(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();
        boolean engaged = this.value > 0 || this.isFocused();
        int target = !this.active ? NeonScreen.DISABLED
                : hovered ? NeonScreen.HOVER
                : engaged ? NeonScreen.ACTIVE : NeonScreen.IDLE;
        float f = NeonScreen.transitionFactor(this.lastNanos, NeonScreen.TRANSITION_SPEED);
        this.lastNanos = System.nanoTime();
        if (!this.initialized) {
            this.currentColor = target;
            this.currentGlow = NeonScreen.glowFor(hovered, engaged);
            this.initialized = true;
        } else {
            this.currentColor = NeonScreen.lerpColor(this.currentColor, target, f);
            this.currentGlow = (int) (this.currentGlow + (NeonScreen.glowFor(hovered, engaged) - this.currentGlow) * f);
        }

        int handleX = this.getX() + (int) (this.value * (this.width - HANDLE_WIDTH));
        int midY = this.getY() + this.height / 2;
        graphics.fill(this.getX() + 1, midY - 1, this.getX() + this.width - 1, midY + 2, this.currentColor);
        graphics.fill(handleX, this.getY() + 2, handleX + HANDLE_WIDTH, this.getY() + this.height - 2, this.currentColor);
        if (this.currentGlow != 0 && NeonScreen.glowVisible(this.currentGlow))
            graphics.outline(handleX - 2, this.getY(), HANDLE_WIDTH + 4, this.height, this.currentGlow);
    }
}
