package io.sniperjohnny.github.mirahud.client.screen;

import io.sniperjohnny.github.mirahud.client.config.inventoryconfig.InventoryConfigManager;
import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonButton;
import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonScreen;
import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import io.sniperjohnny.github.mirahud.client.util.McScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InventoryWidgetManagerScreen extends NeonScreen {
    public Screen parent;

    public InventoryWidgetManagerScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new NeonButton(40, 30, 200, 20,
                Component.translatable(TranslationsKeys.BUTTON_RENDER_INVENTORY_OVERLAY), () -> {
                    InventoryConfigManager.getConfig().showInventoryHud = !InventoryConfigManager.getConfig().showInventoryHud;
                    InventoryConfigManager.save();
                    this.minecraft.player.sendSystemMessage(Component.translatable(TranslationsKeys.MESSAGE_RENDER_INVENTORY_OVERLAY,
                            Component.translatable(InventoryConfigManager.getConfig().showInventoryHud ? "options.on" : "options.off")));
                    rebuildWidgets();
                }).setActiveState(InventoryConfigManager.getConfig().showInventoryHud));

        this.addRenderableWidget(new NeonButton(40, 55, 200, 20,
                Component.translatable(TranslationsKeys.BUTTON_RENDER_ENDERCHEST_WIDGET), () -> {
                    InventoryConfigManager.getConfig().ecWidgetwanted = !InventoryConfigManager.getConfig().ecWidgetwanted;
                    InventoryConfigManager.save();
                    this.minecraft.player.sendSystemMessage(Component.translatable(TranslationsKeys.MESSAGE_RENDER_ENDERCHEST_WIDGET,
                            Component.translatable(InventoryConfigManager.getConfig().ecWidgetwanted ? "options.on" : "options.off")));
                    rebuildWidgets();
                }).setActiveState(InventoryConfigManager.getConfig().ecWidgetwanted));

        this.addRenderableWidget(new NeonButton(40, 80, 200, 20,
                Component.translatable(TranslationsKeys.BUTTON_RENDER_AUCTIONHOUSE_WIDGET), () -> {
                    InventoryConfigManager.getConfig().ahWidgetwanted = !InventoryConfigManager.getConfig().ahWidgetwanted;
                    InventoryConfigManager.save();
                    this.minecraft.player.sendSystemMessage(Component.translatable(TranslationsKeys.MESSAGE_RENDER_AUCTIONHOUSE_WIDGET,
                            Component.translatable(InventoryConfigManager.getConfig().ahWidgetwanted ? "options.on" : "options.off")));
                    rebuildWidgets();
                }).setActiveState(InventoryConfigManager.getConfig().ahWidgetwanted));

        this.addRenderableWidget(new NeonButton(40, 105, 200, 20,
                Component.translatable(TranslationsKeys.BUTTON_RENDER_SELL_WIDGET), () -> {
                    InventoryConfigManager.getConfig().sellWidgetwanted = !InventoryConfigManager.getConfig().sellWidgetwanted;
                    InventoryConfigManager.save();
                    this.minecraft.player.sendSystemMessage(Component.translatable(TranslationsKeys.MESSAGE_RENDER_SELL_WIDGET,
                            Component.translatable(InventoryConfigManager.getConfig().sellWidgetwanted ? "options.on" : "options.off")));
                    rebuildWidgets();
                }).setActiveState(InventoryConfigManager.getConfig().sellWidgetwanted));

        this.addRenderableWidget(new NeonButton(40, 130, 200, 20,
                Component.translatable(TranslationsKeys.BUTTON_RENDER_TRASH_WIDGET), () -> {
                    InventoryConfigManager.getConfig().trashWidgetwanted = !InventoryConfigManager.getConfig().trashWidgetwanted;
                    InventoryConfigManager.save();
                    this.minecraft.player.sendSystemMessage(Component.translatable(TranslationsKeys.MESSAGE_RENDER_TRASH_WIDGET,
                            Component.translatable(InventoryConfigManager.getConfig().trashWidgetwanted ? "options.on" : "options.off")));
                    rebuildWidgets();
                }).setActiveState(InventoryConfigManager.getConfig().trashWidgetwanted));

        this.addRenderableWidget(new NeonButton(40, 155, 200, 20,
                Component.translatable(TranslationsKeys.BUTTON_RENDER_SHOP_WIDGET), () -> {
                    InventoryConfigManager.getConfig().shopWidgetwanted = !InventoryConfigManager.getConfig().shopWidgetwanted;
                    InventoryConfigManager.save();
                    this.minecraft.player.sendSystemMessage(Component.translatable(TranslationsKeys.MESSAGE_RENDER_SHOP_WIDGET,
                            Component.translatable(InventoryConfigManager.getConfig().shopWidgetwanted ? "options.on" : "options.off")));
                    rebuildWidgets();
                }).setActiveState(InventoryConfigManager.getConfig().shopWidgetwanted));

        this.addRenderableWidget(new NeonButton(40, 180, 200, 20,
                Component.translatable(TranslationsKeys.BUTTON_RENDER_MARKET_WIDGET), () -> {
                    InventoryConfigManager.getConfig().marketWidgetwanted = !InventoryConfigManager.getConfig().marketWidgetwanted;
                    InventoryConfigManager.save();
                    this.minecraft.player.sendSystemMessage(Component.translatable(TranslationsKeys.MESSAGE_RENDER_MARKET_WIDGET,
                            Component.translatable(InventoryConfigManager.getConfig().marketWidgetwanted ? "options.on" : "options.off")));
                    rebuildWidgets();
                }).setActiveState(InventoryConfigManager.getConfig().marketWidgetwanted));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 10, 0xFFFFFFFF, true);
    }

    @Override
    public void onClose() {
        McScreens.setScreen(this.minecraft, this.parent);
    }
}