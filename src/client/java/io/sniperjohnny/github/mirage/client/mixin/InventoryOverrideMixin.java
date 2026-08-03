package io.sniperjohnny.github.mirage.client.mixin;

import io.sniperjohnny.github.mirage.client.config.inventoryconfig.InventoryConfigManager;
import io.sniperjohnny.github.mirage.client.widgets.CustomWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(InventoryScreen.class)
public abstract class InventoryOverrideMixin extends Screen {

    protected InventoryOverrideMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addCustomInventoryButton(CallbackInfo ci) {
        // Calculate standard centered coordinates of the 176x166 GUI box
        int xOrigin = (this.width - 176) / 2;
        int yOrigin = (this.height - 166) / 2;

        // Create the button
        Button myButton = Button.builder(
                        Component.literal("Menu"),
                        button -> {
                            if (this.minecraft != null && this.minecraft.player != null) {
                                this.minecraft.player.displayClientMessage(Component.literal("Button Pressed!"), false);
                            }
                        })
                .bounds(xOrigin + 135, yOrigin + 10, 35, 20)
                .build();
        List<CustomWidget> widgets = new ArrayList<>();

        // Create the custom widget with click logic attached
        CustomWidget sellwidget = new CustomWidget(xOrigin , yOrigin - 32, 32, 32, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal("Moneybag Widget Clicked!"), false);
                this.minecraft.player.connection.sendCommand("sell");
                //this.minecraft.player.connection.sendChat("");

            }
        }, "textures/widget_icons/moneybag.png", InventoryConfigManager.getConfig().sellWidgetwanted);
        sellwidget.setTooltip(Tooltip.create(Component.literal("Sell")));

        widgets.add(sellwidget);

        CustomWidget enderchestwidget = new CustomWidget(xOrigin , yOrigin - 32, 32, 32, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("ec");
                //this.minecraft.player.connection.sendChat("");

            }
        }, "ec", InventoryConfigManager.getConfig().ecWidgetwanted);
        enderchestwidget.setTooltip(Tooltip.create(Component.literal("Enderchest")));
        widgets.add(enderchestwidget);

        CustomWidget ahcmdwidget = new CustomWidget(xOrigin , yOrigin - 32, 32, 32, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("ah");
                //this.minecraft.player.connection.sendChat("");

            }
        }, "ah", InventoryConfigManager.getConfig().ahWidgetwanted);
        ahcmdwidget.setTooltip(Tooltip.create(Component.literal("Auction House")));
        widgets.add(ahcmdwidget);


        CustomWidget trashcmdwidget = new CustomWidget(xOrigin , yOrigin - 32, 32, 32, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("trash");
                //this.minecraft.player.connection.sendChat("");

            }
        }, "textures/widget_icons/trashcan.png", InventoryConfigManager.getConfig().trashWidgetwanted);
        trashcmdwidget.setTooltip(Tooltip.create(Component.literal("Trashcan")));

        widgets.add(trashcmdwidget);

        CustomWidget marketcmdwidget = new CustomWidget(xOrigin, yOrigin - 32, 32, 32, ()-> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("market");
                //this.minecraft.player.connection.sendChat("");

            }
        },
        "market", InventoryConfigManager.getConfig().marketWidgetwanted);
        marketcmdwidget.setTooltip(Tooltip.create(Component.literal("Market")));
        widgets.add(marketcmdwidget);

        CustomWidget shopcmdwidget = new CustomWidget(xOrigin, yOrigin - 32, 32, 32, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("shop");
                //this.minecraft.player.connection.sendChat("");

            }
        },
                "shop", InventoryConfigManager.getConfig().shopWidgetwanted);
        shopcmdwidget.setTooltip(Tooltip.create(Component.literal("Shop")));
        widgets.add(shopcmdwidget);

        int forloopran = 0;
        boolean hasdone5loops = false;

        if(InventoryConfigManager.getConfig().showInventoryHud == true) {
        // Add both to the screen
            for(CustomWidget customWidget : widgets) {
                if(customWidget.iswanted) {
                    if(forloopran == 5) {
                        hasdone5loops = true;
                        forloopran = 0;
                    }
                    if(hasdone5loops) {
                        customWidget.setY(yOrigin + 165);
                    }
                    customWidget.setX(xOrigin + 35 * forloopran);
                    this.addRenderableWidget(customWidget);
                    forloopran++;
                }

            }
        }
        /*
        this.addRenderableWidget(sellwidget);
        this.addRenderableWidget(enderchestwidget);
        this.addRenderableWidget(ahcmdwidget);
        this.addRenderableWidget(trashcmdwidget);
        */
    }
}