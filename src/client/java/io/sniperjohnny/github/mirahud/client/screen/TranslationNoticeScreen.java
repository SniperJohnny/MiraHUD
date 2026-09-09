package io.sniperjohnny.github.mirahud.client.screen;

import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonButton;
import io.sniperjohnny.github.mirahud.client.hud_for_client.NeonScreen;
import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import io.sniperjohnny.github.mirahud.client.util.McScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class TranslationNoticeScreen extends NeonScreen {

    private static final int TEXT_MARGIN = 40;

    private final Screen previousScreen;

    public TranslationNoticeScreen(Screen previousScreen) {
        super(Component.translatable(TranslationsKeys.CONFIG_AI_TRANSLATION_NOTICE_TITLE));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        int buttonWidth = 220;
        int buttonHeight = 20;
        this.addRenderableWidget(new NeonButton(
                (this.width - buttonWidth) / 2,
                this.height - 60,
                buttonWidth,
                buttonHeight,
                Component.translatable(TranslationsKeys.CONFIG_AI_TRANSLATION_NOTICE_ACCEPT),
                this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Component brand = Component.literal("MiraHUD");
        graphics.text(this.font, brand, (this.width - this.font.width(brand)) / 2,
                this.height / 2 - 110, NeonScreen.FOLIAGE, true);
        graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2,
                this.height / 2 - 88, 0xFFFFFFFF, true);

        List<FormattedCharSequence> lines = this.font.split(
                Component.translatable(TranslationsKeys.MESSAGE_AI_TRANSLATION_NOTICE),
                this.width - TEXT_MARGIN * 2);
        int y = this.height / 2 - 60;
        int bottomLimit = this.height - 70;
        for (FormattedCharSequence line : lines) {
            if (y > bottomLimit) break;
            graphics.text(this.font, line, (this.width - this.font.width(line)) / 2,
                    y, 0xFFFFFFFF);
            y += this.font.lineHeight + 2;
        }
    }

    @Override
    public void onClose() {
        McScreens.setScreen(Minecraft.getInstance(), previousScreen);
    }
}