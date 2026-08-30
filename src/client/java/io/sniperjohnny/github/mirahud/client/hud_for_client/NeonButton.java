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

    private static final float LERP = 0.15f;

    private final Runnable onClick;
    private boolean activeState;
    private boolean chevron;
    private int currentColor = NeonScreen.IDLE;
    private int currentGlow = 0;

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
        this.currentColor = NeonScreen.lerpColor(this.currentColor, target, LERP);
        this.currentGlow = (int) ((this.currentGlow + (NeonScreen.glowFor(hovered, this.activeState) - this.currentGlow) * LERP));

        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, NeonScreen.ROW);
        if (hovered && this.active) graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x22131A2E);
        graphics.renderOutline(this.getX() - 1, this.getY() - 1, this.width + 2, this.height + 2, this.currentGlow);
        graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, this.currentColor);

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
