package io.sniperjohnny.github.mirahud.client.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

/**
 * EditBox with input filtering. The vanilla {@code EditBox.setFilter} API was
 * removed in 26.1, so the filter is enforced by validating the text that would
 * result from an insertion and rejecting it when it would not be valid.
 */
public class FilteredEditBox extends EditBox {

    private final Predicate<String> filter;

    public FilteredEditBox(Font font, int x, int y, int width, int height, Component message, Predicate<String> filter) {
        super(font, x, y, width, height, message);
        this.filter = filter;
    }

    @Override
    public void insertText(String text) {
        if (!this.filter.test(text)) return;
        super.insertText(text);
    }
}