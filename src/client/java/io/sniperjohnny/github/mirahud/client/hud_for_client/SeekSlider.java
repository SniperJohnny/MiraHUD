package io.sniperjohnny.github.mirahud.client.hud_for_client;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/**
 * VLC-style seek bar: dragging scrubs the position visually, seeking happens
 * once on release so the expensive ffmpeg restart only runs a single time.
 */
public class SeekSlider extends NeonSlider {

    private final DoubleConsumer onSeek;
    private boolean userDragging;

    public SeekSlider(int x, int y, int width, int height, double value, DoubleConsumer onSeek) {
        super(x, y, width, height, Component.literal("Seek"), value);
        this.onSeek = onSeek;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.userDragging = this.isMouseOver(event.x(), event.y());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = this.userDragging;
        boolean handled = super.mouseReleased(event);
        if (wasDragging && this.onSeek != null) {
            this.onSeek.accept(this.value);
        }
        this.userDragging = false;
        return handled;
    }

    @Override
    protected void updateMessage() {
    }

    @Override
    protected void applyValue() {
    }

    public void setSeekValue(double value) {
        super.setValue(value);
    }

    public boolean isUserDragging() {
        return this.userDragging;
    }
}