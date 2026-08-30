package io.sniperjohnny.github.mirahud.client.hud_for_client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class NeonScreen extends Screen {

    public static final int PANEL = 0xCC0B1020;
    public static final int ROW = 0x66131A2E;
    public static final int IDLE = 0xFF5FB2FF;
    public static final int HOVER = 0xFF37E0FF;
    public static final int ACTIVE = 0xFF6BFF8F;
    public static final int GLOW_HOVER = 0x6637E0FF;
    public static final int GLOW_ACTIVE = 0x4D6BFF8F;
    public static final int DISABLED = 0xFF3A3F4E;

    protected NeonScreen(Component title) {
        super(title);
    }

    public static int lerpColor(int from, int to, float t) {
        int a = (from >>> 24) & 0xFF, r = (from >>> 16) & 0xFF, g = (from >>> 8) & 0xFF, b = from & 0xFF;
        int ta = (to >>> 24) & 0xFF, tr = (to >>> 16) & 0xFF, tg = (to >>> 8) & 0xFF, tb = to & 0xFF;
        return (int) (a + (ta - a) * t) << 24
                | (int) (r + (tr - r) * t) << 16
                | (int) (g + (tg - g) * t) << 8
                | (int) (b + (tb - b) * t);
    }

    public static int glowFor(boolean hovered, boolean active) {
        if (hovered) return GLOW_HOVER;
        return active ? GLOW_ACTIVE : 0;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fillGradient(0, 0, this.width, this.height, PANEL, 0xE60A0E1C);
        graphics.fill(0, 22, this.width, 23, IDLE);
    }
}
