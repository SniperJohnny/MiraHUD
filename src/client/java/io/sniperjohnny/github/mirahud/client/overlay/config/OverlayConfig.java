package io.sniperjohnny.github.mirahud.client.overlay.config;

import io.sniperjohnny.github.mirahud.client.translationskeys.TranslationsKeys;
import net.minecraft.util.ARGB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OverlayConfig {
    public String id = UUID.randomUUID().toString();

    public String mediaType = "image";

    public boolean enabled = false;
    public String sourcePath = "";
    public int posX = 10;
    public int posY = 10;
    public int width = 100;
    public int height = 100;
    public float opacity = 1.0f;
    public float volume = 1.0f;
    public Anchor anchor = Anchor.TOP_LEFT;
    public boolean lockAspectRatio = true;
    public boolean playing = true;
    public boolean loop = false;

    public Map<String, String> options = new HashMap<>();

    public int getColor() {
        return ARGB.color((int) (this.opacity * 255), 255, 255, 255);
    }

    public boolean isVideo() {
        return "video".equals(mediaType);
    }

    public enum Anchor {
        TOP_LEFT(TranslationsKeys.ANCHOR_TOP_LEFT, "Top Left"),
        TOP_RIGHT(TranslationsKeys.ANCHOR_TOP_RIGHT, "Top Right"),
        BOTTOM_LEFT(TranslationsKeys.ANCHOR_BOTTOM_LEFT, "Bottom Left"),
        BOTTOM_RIGHT(TranslationsKeys.ANCHOR_BOTTOM_RIGHT, "Bottom Right"),
        CENTER(TranslationsKeys.ANCHOR_CENTER, "Center");

        public final String translationKey;
        public final String displayName;

        Anchor(String translationKey, String displayName) {
            this.translationKey = translationKey;
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
        public String getTranslationKey() { return translationKey; }
    }
}
