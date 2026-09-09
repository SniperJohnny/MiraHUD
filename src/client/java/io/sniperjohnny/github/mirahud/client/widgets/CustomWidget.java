package io.sniperjohnny.github.mirahud.client.widgets;

import io.sniperjohnny.github.mirahud.MiraHUD;
import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class CustomWidget extends AbstractWidget {
    private final Runnable onPress;
    private final String iconFilepath;
    public boolean wanted;

    public CustomWidget(int x, int y, int width, int height, Runnable onPress, String iconFilepath, boolean wanted) {
        super(x, y, width, height, Component.empty());
        this.onPress = onPress;
        this.iconFilepath = iconFilepath;
        this.wanted = wanted;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event, doubleClick);
        if (this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHovered();
        int outline = hovered ? NeonScreen.FOLIAGE : NeonScreen.WOOD;
        int x = this.getX(), y = this.getY(), w = this.width, h = this.height;

        graphics.fill(x, y, x + w, y + h, 0xFF141414);
        if (hovered) graphics.fill(x, y, x + w, y + h, NeonScreen.ROW_HOVER);
        if (hovered) graphics.outline(x - 2, y - 2, w + 4, h + 4, NeonScreen.GLOW_HOVER);
        // chunky 2px pixel border
        graphics.fill(x, y, x + w, y + 2, outline);
        graphics.fill(x, y + h - 2, x + w, y + h, outline);
        graphics.fill(x, y, x + 2, y + h, outline);
        graphics.fill(x + w - 2, y, x + w, y + h, outline);
        // pixel corner blocks
        graphics.fill(x - 1, y - 1, x + 1, y + 1, outline);
        graphics.fill(x + w - 1, y - 1, x + w + 1, y + 1, outline);
        graphics.fill(x - 1, y + h - 1, x + 1, y + h + 1, outline);
        graphics.fill(x + w - 1, y + h - 1, x + w + 1, y + h + 1, outline);

        switch (this.iconFilepath) {
            case "ec":
                renderScaledItem(graphics, new ItemStack(Blocks.ENDER_CHEST));
                break;
            case "ah":
                renderScaledItem(graphics, new ItemStack(Blocks.CHEST));
                break;
            case "market":
                renderScaledItem(graphics, new ItemStack(Blocks.RAW_GOLD_BLOCK));
                break;
            case "shop":
                renderScaledItem(graphics, new ItemStack(Blocks.GOLD_BLOCK));
                break;
            default:
                Identifier iconTexture = Identifier.fromNamespaceAndPath(MiraHUD.MOD_ID, this.iconFilepath);

                int padding = 4;
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        iconTexture,
                        this.getX() + padding,
                        this.getY() + padding,
                        0,
                        0,
                        this.width - (padding * 2),
                        this.height - (padding * 2),
                        this.width - (padding * 2),
                        this.height - (padding * 2)
                );
                break;
        }
    }

    private void renderScaledItem(GuiGraphicsExtractor graphics, ItemStack itemStack) {
        float scaleX = (float) this.width / 16.0f;
        float scaleY = (float) this.height / 16.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(this.getX(), this.getY());
        graphics.pose().scale(scaleX, scaleY);

        graphics.item(itemStack, 0, 0);
        graphics.pose().popMatrix();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }

    public boolean isWanted() {
        return this.wanted;
    }
}
