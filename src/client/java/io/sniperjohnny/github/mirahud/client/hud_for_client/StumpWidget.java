package io.sniperjohnny.github.mirahud.client.hud_for_client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The big clickable stump in the center of the screen. Clicking it grows a new
 * branch (adds an overlay). Draws a chunky trunk-base flare with roots and a
 * green "+" to signal "grow here". The pixel art scales with the widget size.
 */
public class StumpWidget extends AbstractWidget {

    public static final int STUMP_W = 64;
    public static final int STUMP_H = 40;
    private static final int BASE_H = 18;

    private final Runnable onAdd;

    public StumpWidget(int x, int y, Component message, Runnable onAdd) {
        super(x, y, STUMP_W, STUMP_H, message);
        this.onAdd = onAdd;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        onAdd.run();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (this.active && this.isFocused() && (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            onAdd.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int s = Math.max(1, this.height / BASE_H);
        int cx = this.getX() + this.width / 2;
        int top = this.getY();
        int bot = this.getY() + this.height;

        // trunk stub connecting into the flare
        graphics.fill(cx - 2 * s, top, cx + 2 * s, top + 2 * s, NeonScreen.WOOD);
        // flare
        graphics.fill(cx - 6 * s, top + 2 * s, cx + 6 * s, bot, NeonScreen.WOOD);
        graphics.fill(cx - 6 * s, bot - 2 * s, cx + 6 * s, bot, NeonScreen.WOOD_DARK);
        // roots
        graphics.fill(cx - 9 * s, bot - 3 * s, cx - 6 * s, bot, NeonScreen.WOOD_DARK);
        graphics.fill(cx + 6 * s, bot - 3 * s, cx + 9 * s, bot, NeonScreen.WOOD_DARK);

        // hover glow
        if (this.isHovered()) {
            graphics.renderOutline(cx - 11 * s, top - 2, 22 * s, this.height + 4, NeonScreen.GLOW_HOVER);
        }

        // green plus: "grow a branch here"
        int cy = top + this.height / 2;
        graphics.fill(cx - s, cy, cx + 2 * s, cy + s, NeonScreen.FOLIAGE);
        graphics.fill(cx, cy - s, cx + s, cy + 2 * s, NeonScreen.FOLIAGE);
    }
}