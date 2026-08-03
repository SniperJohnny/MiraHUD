package io.sniperjohnny.github.mirahud.client.screen;

import io.sniperjohnny.github.mirahud.client.config.inventoryconfig.InventoryConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class InventoryWidgetManagerScreen extends Screen {
    public Screen parent;
    public InventoryWidgetManagerScreen(Component title, Screen parent) {
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
            Button renderInventoryOverlayButton = Button.builder(Component.literal("Render Inventory overlay"), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
                InventoryConfigManager.getConfig().showInventoryHud = !InventoryConfigManager.getConfig().showInventoryHud;
                InventoryConfigManager.save();
                String renderhudtext = InventoryConfigManager.getConfig().showInventoryHud ? "True" : "False";
                this.minecraft.player.displayClientMessage(Component.literal("Render InventoryWidgets " + renderhudtext), false);

            }).bounds(40, 30, 200, 20).build();


            Button renderEnderchestWidgetButton = Button.builder(Component.literal("Render Enderchest Widget"), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
                InventoryConfigManager.getConfig().ecWidgetwanted = !InventoryConfigManager.getConfig().ecWidgetwanted;
                InventoryConfigManager.save();
                String renderhudtext = InventoryConfigManager.getConfig().ecWidgetwanted ? "True" : "False";
                this.minecraft.player.displayClientMessage(Component.literal("Render Enderchest Widget " + renderhudtext), false);

            }).bounds(40, 55, 200, 20).build();

            Button renderahWidgetButton = Button.builder(Component.literal("Render Auctionhouse Widget"), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
                InventoryConfigManager.getConfig().ahWidgetwanted = !InventoryConfigManager.getConfig().ahWidgetwanted;
                InventoryConfigManager.save();
                String renderhudtext = InventoryConfigManager.getConfig().ahWidgetwanted ? "True" : "False";
                this.minecraft.player.displayClientMessage(Component.literal("Render Auctionhouse Widget " + renderhudtext), false);

            }).bounds(40, 80, 200, 20).build();


            Button rendersellWidgetButton = Button.builder(Component.literal("Render Sell Widget"), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
                InventoryConfigManager.getConfig().sellWidgetwanted = !InventoryConfigManager.getConfig().sellWidgetwanted;
                InventoryConfigManager.save();
                String renderhudtext = InventoryConfigManager.getConfig().sellWidgetwanted ? "True" : "False";
                this.minecraft.player.displayClientMessage(Component.literal("Render Auctionhouse Widget " + renderhudtext), false);

            }).bounds(40, 105, 200, 20).build();
            Button rendertrashWidgetButton = Button.builder(Component.literal("Render Trash Widget"), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
                InventoryConfigManager.getConfig().trashWidgetwanted = !InventoryConfigManager.getConfig().trashWidgetwanted;
                InventoryConfigManager.save();
                String renderhudtext = InventoryConfigManager.getConfig().trashWidgetwanted ? "True" : "False";
                this.minecraft.player.displayClientMessage(Component.literal("Render Trash Widget " + renderhudtext), false);

            }).bounds(40, 130, 200, 20).build();
            Button rendershopWidgetButton = Button.builder(Component.literal("Render Trash Widget"), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
                InventoryConfigManager.getConfig().shopWidgetwanted = !InventoryConfigManager.getConfig().shopWidgetwanted;
                InventoryConfigManager.save();
                String renderhudtext = InventoryConfigManager.getConfig().shopWidgetwanted ? "True" : "False";
                this.minecraft.player.displayClientMessage(Component.literal("Render Shop Widget " + renderhudtext), false);

            }).bounds(40, 155, 200, 20).build();
            Button rendermarketWidgetButton = Button.builder(Component.literal("Render Trash Widget"), (btn) -> {
                // When the button is clicked, we can display a toast to the screen.
                InventoryConfigManager.getConfig().marketWidgetwanted = !InventoryConfigManager.getConfig().marketWidgetwanted;
                InventoryConfigManager.save();
                String renderhudtext = InventoryConfigManager.getConfig().marketWidgetwanted ? "True" : "False";
                this.minecraft.player.displayClientMessage(Component.literal("Render Market Widget " + renderhudtext), false);

            }).bounds(40, 155, 200, 20).build();





            List<Button> buttons = new ArrayList<>();
            buttons.add(rendertrashWidgetButton);
            buttons.add(renderahWidgetButton);
            buttons.add(rendersellWidgetButton);
            buttons.add(renderEnderchestWidgetButton);
            buttons.add(renderInventoryOverlayButton);
            buttons.add(rendershopWidgetButton);
            buttons.add(rendermarketWidgetButton);
            // x, y, width, height
            // It's recommended to use the fixed height of 20 to prevent rendering issues with the button
            // textures.
            // Register the button widget.
            for(Button renderbutton : buttons) {
                this.addRenderableWidget(renderbutton);
            }
        }
        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);
            // Minecraft doesn't have a "label" widget, so we'll have to draw our own text.
            // We'll subtract the font height from the Y position to make the text appear above the button.
            // Subtracting an extra 10 pixels will give the text some padding.
            // textRenderer, text, x, y, color, hasShadow
            //graphics.drawString(this.font, "Special Button", 40, 40 - this.font.lineHeight - 10, 0xFFFFFFFF, true);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.parent);

        }
    }

