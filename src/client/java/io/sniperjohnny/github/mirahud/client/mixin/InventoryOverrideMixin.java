package io.sniperjohnny.github.mirahud.client.mixin;

import io.sniperjohnny.github.mirahud.client.config.inventoryconfig.InventoryConfigManager;
import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import io.sniperjohnny.github.mirahud.client.widgets.CustomWidget;
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

    private static final int WIDGET_SIZE = 32;
    private static final int WIDGET_SPACING = 35;
    private static final int WIDGETS_PER_ROW = 5;

    protected InventoryOverrideMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addCustomInventoryButton(CallbackInfo ci) {
        int xOrigin = (this.width - 176) / 2;
        int yOrigin = (this.height - 166) / 2;

        List<CustomWidget> widgets = new ArrayList<>();

        CustomWidget sellWidget = new CustomWidget(xOrigin, yOrigin - 32, WIDGET_SIZE, WIDGET_SIZE, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable(TranslationsKeys.MESSAGE_MONEYBAG_CLICKED), false);
                this.minecraft.player.connection.sendCommand("sell");
            }
        }, "textures/widget_icons/moneybag.png", InventoryConfigManager.getConfig().sellWidgetwanted);
        sellWidget.setTooltip(Tooltip.create(Component.translatable(TranslationsKeys.WIDGET_SELL)));

        widgets.add(sellWidget);

        CustomWidget enderchestWidget = new CustomWidget(xOrigin, yOrigin - 32, WIDGET_SIZE, WIDGET_SIZE, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("ec");
            }
        }, "ec", InventoryConfigManager.getConfig().ecWidgetwanted);
        enderchestWidget.setTooltip(Tooltip.create(Component.translatable(TranslationsKeys.WIDGET_ENDERCHEST)));
        widgets.add(enderchestWidget);

        CustomWidget auctionHouseWidget = new CustomWidget(xOrigin, yOrigin - 32, WIDGET_SIZE, WIDGET_SIZE, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("ah");
            }
        }, "ah", InventoryConfigManager.getConfig().ahWidgetwanted);
        auctionHouseWidget.setTooltip(Tooltip.create(Component.translatable(TranslationsKeys.WIDGET_AUCTION_HOUSE)));
        widgets.add(auctionHouseWidget);


        CustomWidget trashWidget = new CustomWidget(xOrigin, yOrigin - 32, WIDGET_SIZE, WIDGET_SIZE, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("trash");
            }
        }, "textures/widget_icons/trashcan.png", InventoryConfigManager.getConfig().trashWidgetwanted);
        trashWidget.setTooltip(Tooltip.create(Component.translatable(TranslationsKeys.WIDGET_TRASHCAN)));

        widgets.add(trashWidget);

        CustomWidget marketWidget = new CustomWidget(xOrigin, yOrigin - 32, WIDGET_SIZE, WIDGET_SIZE, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("market");
            }
        },
        "market", InventoryConfigManager.getConfig().marketWidgetwanted);
        marketWidget.setTooltip(Tooltip.create(Component.translatable(TranslationsKeys.WIDGET_MARKET)));
        widgets.add(marketWidget);

        CustomWidget shopWidget = new CustomWidget(xOrigin, yOrigin - 32, WIDGET_SIZE, WIDGET_SIZE, () -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.connection.sendCommand("shop");
            }
        },
                "shop", InventoryConfigManager.getConfig().shopWidgetwanted);
        shopWidget.setTooltip(Tooltip.create(Component.translatable(TranslationsKeys.WIDGET_SHOP)));
        widgets.add(shopWidget);

        if (InventoryConfigManager.getConfig().showInventoryHud) {
            int widgetIndex = 0;
            boolean secondRowStarted = false;
            for (CustomWidget customWidget : widgets) {
                if (!customWidget.wanted) {
                    continue;
                }
                if (widgetIndex == WIDGETS_PER_ROW) {
                    secondRowStarted = true;
                    widgetIndex = 0;
                }
                if (secondRowStarted) {
                    customWidget.setY(yOrigin + 165);
                }
                customWidget.setX(xOrigin + WIDGET_SPACING * widgetIndex);
                this.addRenderableWidget(customWidget);
                widgetIndex++;
            }
        }
    }
}
