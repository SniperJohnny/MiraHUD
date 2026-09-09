package io.sniperjohnny.github.mirahud.client.hud_for_client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class NeonScreen extends Screen {

    // HUO pixel-art palette (tree + heart logo on black)
    public static final int PANEL = 0xCC000000;
    public static final int PANEL_BOTTOM = 0xE8000000;
    // Opaque fills: semi-transparent widget fills ghost through each other and the
    // glow rings, which reads as flicker on state transitions.
    public static final int ROW = 0xFF1C1C1C;
    public static final int ROW_HOVER = 0xFF282828;

    public static final int WOOD = 0xFFC9A05B;        // light tan trunk - idle
    public static final int WOOD_DARK = 0xFF7A5427;   // brown trunk shade
    public static final int FOLIAGE = 0xFF33E85E;     // vivid neon green leaves - hovered
    public static final int FOLIAGE_DARK = 0xFF20642F;// forest green leaves
    public static final int HEART = 0xFF9C8FB8;       // muted purple-gray heart - activated
    public static final int HEART_DARK = 0xFF5A4E70;  // shadowy purple heart
    public static final int DISABLED = 0xFF3E3E46;
    public static final int GLOW_HOVER = 0x6633E85E;
    public static final int GLOW_ACTIVE = 0x4D9C8FB8;

    // State aliases (keep the neon-era names working)
    public static final int IDLE = WOOD;
    public static final int HOVER = FOLIAGE;
    public static final int ACTIVE = HEART;

    // Smooth color/glow transitions: framerate-independent exponential smoothing
    // (full color change in ~150-200ms), instead of a fixed per-frame lerp that
    // speeds up or slows down with the FPS and can shimmer.
    public static final float TRANSITION_SPEED = 12f;

    public static float transitionFactor(long lastNanos, float speed) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNanos) / 1_000_000_000f, 0.1f);
        return 1f - (float) Math.exp(-speed * dt);
    }

    // Don't bother drawing glow rings whose alpha is still below visibility.
    public static boolean glowVisible(int glow) {
        return ((glow >>> 24) & 0xFF) >= 0x10;
    }

    // Small 5x5 pixel heart, scaled 2x, drawn in the top-left corner.
    private static final String[] HEART_PIXELS = {
            "01010",
            "11111",
            "11111",
            "01110",
            "00100"
    };

    protected NeonScreen(Component title) {
        super(title);
    }

    protected void rebuildWidgets() {
        this.clearWidgets();
        this.init();
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fillGradient(0, 0, this.width, this.height, PANEL, PANEL_BOTTOM);
        drawTopEdgePixels(graphics);
        drawPixelHeart(graphics, 7, 6);
        graphics.fill(0, 22, this.width, 24, WOOD);
        graphics.fill(0, 24, this.width, 25, WOOD_DARK);
    }

    private void drawTopEdgePixels(GuiGraphicsExtractor graphics) {
        // Scattered leaf/tan pixels along the top edge, like the logo background.
        for (int i = 0; i < Math.max(4, this.width / 48); i++) {
            int x = i * 48 + (i * 13) % 17;
            if (x < 0 || x >= this.width - 8) continue;
            graphics.fill(x, 0, x + 2, 2, FOLIAGE_DARK);
            graphics.fill(x + 3, 0, x + 4, 1, WOOD);
        }
    }

    private void drawPixelHeart(GuiGraphicsExtractor graphics, int x, int y) {
        int scale = 2;
        for (int py = 0; py < HEART_PIXELS.length; py++) {
            int color = py >= 3 ? HEART_DARK : HEART;
            for (int px = 0; px < HEART_PIXELS[py].length(); px++) {
                if (HEART_PIXELS[py].charAt(px) != '1') continue;
                graphics.fill(x + px * scale, y + py * scale, x + px * scale + scale, y + py * scale + scale, color);
            }
        }
    }
}