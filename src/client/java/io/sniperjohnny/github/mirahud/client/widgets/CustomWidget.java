package io.sniperjohnny.github.mirahud.client.widgets;

import io.sniperjohnny.github.mirahud.MiraHUD;
import net.minecraft.client.gui.GuiGraphics;
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

    private static final Identifier BUTTON_SPRITE = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("widget/button_highlighted");

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
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Identifier backgroundSprite = this.isHovered() ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE;

        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                backgroundSprite,
                this.getX(),
                this.getY(),
                this.width,
                this.height
        );

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

    private void renderScaledItem(GuiGraphics graphics, ItemStack itemStack) {
        float scaleX = (float) this.width / 16.0f;
        float scaleY = (float) this.height / 16.0f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(this.getX(), this.getY());
        graphics.pose().scale(scaleX, scaleY);

        graphics.renderItem(itemStack, 0, 0);
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
