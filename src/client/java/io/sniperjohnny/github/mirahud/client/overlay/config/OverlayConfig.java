package io.sniperjohnny.github.mirahud.client.overlay.config;

import net.minecraft.util.ARGB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OverlayConfig {
    /** Unique identifier for this overlay (used to key media providers). */
    public String id = UUID.randomUUID().toString();

    /** Media source type. "image" or "video" (local file). URL playback has been removed. */
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
    /** When true, the video automatically restarts from the beginning after it finishes playing. */
    public boolean loop = false;

    /**
     * General-purpose key-value options for per-overlay preferences and mod data.
     * Use this to store arbitrary settings like player-specific preferences,
     * display flags, custom rendering options, etc.
     */
    public Map<String, String> options = new HashMap<>();

    public int getColor() {
        return ARGB.color((int) (this.opacity * 255), 255, 255, 255);
    }

    /** @return true if this is a local video overlay (needs per-frame ticking). */
    public boolean isVideo() {
        return "video".equals(mediaType);
    }

    public enum Anchor {
        TOP_LEFT("anchor.mirahud.top_left", "Top Left"),
        TOP_RIGHT("anchor.mirahud.top_right", "Top Right"),
        BOTTOM_LEFT("anchor.mirahud.bottom_left", "Bottom Left"),
        BOTTOM_RIGHT("anchor.mirahud.bottom_right", "Bottom Right"),
        CENTER("anchor.mirahud.center", "Center");

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
