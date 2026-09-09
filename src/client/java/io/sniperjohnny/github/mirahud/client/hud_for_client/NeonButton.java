package io.sniperjohnny.github.mirahud.client.hud_for_client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class NeonButton extends AbstractWidget {

    private final Runnable onClick;
    private boolean activeState;
    private boolean chevron;
    private int currentColor = NeonScreen.IDLE;
    private int currentGlow = 0;
    private long lastNanos = System.nanoTime();
    private boolean initialized;

    public NeonButton(int x, int y, int width, int height, Component message, Runnable onClick) {
        super(x, y, width, height, message);
        this.onClick = onClick;
    }

    public NeonButton setActiveState(boolean activeState) {
        this.activeState = activeState;
        return this;
    }

    public NeonButton setEnabled(boolean enabled) {
        this.active = enabled;
        return this;
    }

    public NeonButton setChevron(boolean chevron) {
        this.chevron = chevron;
        return this;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onClick.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.active && this.isFocused() && (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            onClick.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();
        int target = !this.active ? NeonScreen.DISABLED
                : hovered ? NeonScreen.HOVER
                : this.activeState ? NeonScreen.ACTIVE : NeonScreen.IDLE;
        float f = NeonScreen.transitionFactor(this.lastNanos, NeonScreen.TRANSITION_SPEED);
        this.lastNanos = System.nanoTime();
        if (!this.initialized) {
            // Snap to the target state on first render so screen rebuilds don't blink.
            this.currentColor = target;
            this.currentGlow = NeonScreen.glowFor(hovered, this.activeState);
            this.initialized = true;
        } else {
            this.currentColor = NeonScreen.lerpColor(this.currentColor, target, f);
            this.currentGlow = (int) (this.currentGlow + (NeonScreen.glowFor(hovered, this.activeState) - this.currentGlow) * f);
        }

        int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
        graphics.fill(x, y, x + w, y + h, NeonScreen.ROW);
        if (hovered && this.active) graphics.fill(x, y, x + w, y + h, NeonScreen.ROW_HOVER);
        if (this.currentGlow != 0 && NeonScreen.glowVisible(this.currentGlow)) graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, this.currentGlow);
        // chunky 2px pixel border
        graphics.fill(x, y, x + w, y + 2, this.currentColor);
        graphics.fill(x, y + h - 2, x + w, y + h, this.currentColor);
        graphics.fill(x, y, x + 2, y + h, this.currentColor);
        graphics.fill(x + w - 2, y, x + w, y + h, this.currentColor);
        // pixel corner blocks
        graphics.fill(x - 1, y - 1, x + 1, y + 1, this.currentColor);
        graphics.fill(x + w - 1, y - 1, x + w + 1, y + 1, this.currentColor);
        graphics.fill(x - 1, y + h - 1, x + 1, y + h + 1, this.currentColor);
        graphics.fill(x + w - 1, y + h - 1, x + w + 1, y + h + 1, this.currentColor);

        Component label = fitLabel(this.getMessage());
        int textRightInset = this.chevron ? 14 : 0;
        int textX = this.getX() + (this.width - Minecraft.getInstance().font.width(label) - textRightInset) / 2;
        int textY = this.getY() + (this.height - 8) / 2;
        graphics.drawString(Minecraft.getInstance().font, label, textX, textY, 0xFFFFFFFF, true);
        if (this.chevron) {
            graphics.drawString(Minecraft.getInstance().font, "▾", this.getX() + this.width - 9, textY, 0xFFFFFFFF, true);
        }
    }

    private Component fitLabel(Component label) {
        int maxTextWidth = this.width - (this.chevron ? 16 : 0) - 6;
        if (Minecraft.getInstance().font.width(label) <= maxTextWidth) return label;
        int ellipsisWidth = Minecraft.getInstance().font.width("...");
        String plain = StringUtil.stripColor(label.getString());
        String clipped = Minecraft.getInstance().font.plainSubstrByWidth(plain, Math.max(0, maxTextWidth - ellipsisWidth));
        return Component.literal(clipped + "...");
    }
}
