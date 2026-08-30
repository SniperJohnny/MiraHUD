package io.sniperjohnny.github.mirahud.client.hud_for_client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class MediaTypeSelectionScreen extends NeonScreen {

    private final Screen parent;
    private final String currentTypeId;
    private final Consumer<String> onPicked;

    public MediaTypeSelectionScreen(Screen parent, Component title, String currentTypeId, Consumer<String> onPicked) {
        super(title);
        this.parent = parent;
        this.currentTypeId = currentTypeId;
        this.onPicked = onPicked;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(220, this.width - 80);
        int x = (this.width - buttonWidth) / 2;
        int y = this.height / 2 - MediaTypes.ALL.size() * 12;
        for (MediaTypes.Type type : MediaTypes.ALL) {
            NeonButton button = new NeonButton(x, y, buttonWidth, 20,
                    Component.translatable(type.translationKey()),
                    () -> {
                        onPicked.accept(type.id());
                        Minecraft.getInstance().setScreen(parent);
                    })
                    .setActiveState(type.id().equals(currentTypeId));
            this.addRenderableWidget(button);
            y += 24;
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
