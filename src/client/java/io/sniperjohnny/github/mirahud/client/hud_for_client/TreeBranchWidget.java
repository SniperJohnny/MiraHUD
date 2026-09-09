package io.sniperjohnny.github.mirahud.client.hud_for_client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * One overlay shown as a branch of the tree in the middle of the screen.
 * Branches alternate left/right from the centered trunk. Enabled overlays grow
 * a full leaf canopy; video canopies sway in the wind (animated), image
 * canopies stay static. The selected overlay gets a pixel heart pinned on the
 * trunk.
 */
public class TreeBranchWidget extends AbstractWidget {

    public static final int TRUNK_W = 4;
    public static final int BRANCH_LEN = 28;
    public static final int BRANCH_Y_OFFSET = 12;

    // 13x7 full canopy. G = bright foliage, g = dark foliage.
    private static final String[] CANOPY = {
            ".....GGG.....",
            "...GGGGGGG...",
            "..GGGGGGGGG..",
            ".GGGGGGGGGGG.",
            ".GGgGGGGGgGG.",
            "..GGGGGGGGG..",
            "...GGGGGGG..."
    };
    private static final String[] HEART = {
            "01010",
            "11111",
            "11111",
            "01110",
            "00100"
    };

    private final int trunkX;
    private final boolean branchLeft;
    private final Runnable onClick;
    private final Component label;
    private final boolean hasLeaves;
    private final boolean animatedLeaves;
    private final boolean selected;

    public TreeBranchWidget(int x, int y, int width, int height, int trunkX, boolean branchLeft, Component label,
                            String fullPath, boolean hasLeaves, boolean animatedLeaves, boolean selected, Runnable onClick) {
        super(x, y, width, height, label);
        this.trunkX = trunkX;
        this.branchLeft = branchLeft;
        this.label = label;
        this.hasLeaves = hasLeaves;
        this.animatedLeaves = animatedLeaves;
        this.selected = selected;
        this.onClick = onClick;
        if (fullPath != null && !fullPath.isBlank()) {
            this.setTooltip(Tooltip.create(Component.literal(fullPath)));
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.playDownSound(Minecraft.getInstance().getSoundManager());
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
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int branchY = this.getY() + BRANCH_Y_OFFSET;
        int inner = TRUNK_W / 2;
        int tipX = branchLeft ? trunkX - inner - BRANCH_LEN : trunkX + inner + BRANCH_LEN;

        // branch: 2px tall, growing from the trunk
        if (branchLeft) {
            graphics.fill(tipX, branchY, trunkX - inner, branchY + 2, NeonScreen.WOOD);
            graphics.fill(tipX, branchY + 2, trunkX - inner, branchY + 3, NeonScreen.WOOD_DARK);
        } else {
            graphics.fill(trunkX + inner, branchY, tipX, branchY + 2, NeonScreen.WOOD);
            graphics.fill(trunkX + inner, branchY + 2, tipX, branchY + 3, NeonScreen.WOOD_DARK);
        }
        // twig: small up-tick at the tip
        graphics.fill(tipX - 1, branchY - 4, tipX + 1, branchY + 2, NeonScreen.WOOD);

        // selected overlay gets a pixel heart pinned on the trunk
        if (this.selected) drawHeart(graphics, trunkX - 2, branchY - 9);

        // glow around the canopy
        if (this.isHovered()) {
            graphics.renderOutline(tipX - 7, branchY - 11, 15, 9, NeonScreen.GLOW_HOVER);
        } else if (this.selected) {
            graphics.renderOutline(tipX - 7, branchY - 11, 15, 9, NeonScreen.GLOW_ACTIVE);
        }

        if (this.hasLeaves) drawCanopy(graphics, tipX, branchY);

        // label hanging under the branch, on its own side
        Component lbl = this.label;
        int lw = Minecraft.getInstance().font.width(lbl);
        int lx = branchLeft
                ? Math.max(this.getX(), tipX - 8 - lw)
                : Math.min(this.getX() + this.width - lw, tipX + 8);
        graphics.drawString(Minecraft.getInstance().font, lbl, lx, branchY + 4, 0xFFFFFFFF, true);
    }

    private void drawCanopy(GuiGraphics graphics, int tipX, int branchY) {
        int x = tipX - 6;
        int y = branchY - 10;
        if (this.animatedLeaves) {
            // video leaves sway one pixel back and forth
            double phase = (System.currentTimeMillis() % 1600L) / 1600.0 * Math.PI * 2;
            x += (int) Math.round(Math.sin(phase));
        }
        for (int py = 0; py < CANOPY.length; py++) {
            for (int px = 0; px < CANOPY[py].length(); px++) {
                char c = CANOPY[py].charAt(px);
                if (c == '.') continue;
                int color = c == 'G' ? NeonScreen.FOLIAGE : NeonScreen.FOLIAGE_DARK;
                graphics.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
    }

    private void drawHeart(GuiGraphics graphics, int x, int y) {
        for (int py = 0; py < HEART.length; py++) {
            int color = py >= 3 ? NeonScreen.HEART_DARK : NeonScreen.HEART;
            for (int px = 0; px < HEART[py].length(); px++) {
                if (HEART[py].charAt(px) != '1') continue;
                graphics.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
    }
}