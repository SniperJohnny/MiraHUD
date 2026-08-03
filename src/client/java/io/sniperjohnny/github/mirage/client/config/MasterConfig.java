package io.sniperjohnny.github.mirage.client.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Master configuration — the central place for all player preferences
 * and global mod settings.
 * <p>
 * This is the single source of truth for anything that isn't specific
 * to one overlay.  Use the typed fields for common settings and the
 * {@code custom} map for ad-hoc key-value data.
 * <p>
 * <b>Usage example:</b>
 * <pre>{@code
 *   MasterConfig cfg = MasterConfigManager.getConfig();
 *   cfg.showOverlayByDefault = true;
 *   cfg.globalVolume = 0.8f;
 *   cfg.set("lastPickedDir", "/home/user/videos");
 *   cfg.save();
 * }</pre>
 */
public class MasterConfig {

    /** Gson needs a no-arg constructor. */
    public MasterConfig() {}

    // ==================== Typed preferences (safe defaults) ====================

    /** Whether new overlays start enabled by default. */
    public boolean showOverlayByDefault = false;

    /** Global volume multiplier applied to all video overlays (0.0 – 1.0). */
    public float globalVolume = 1.0f;

    /** Maximum video output height; 0 = use native resolution. */
    public int maxVideoHeight = 1080;

    /** Last directory the file picker was opened in (so Browse remembers). */
    public String lastBrowseDirectory = "";

    /**
     * Whether hardware acceleration is enabled for FFmpeg.
     * Off by default: {@code -hwaccel auto} is known to crash on some Windows
     * GPU/driver combos, so software decoding (CPU-only) is the safe default.
     */
    public boolean ffmpegHardwareAccel = false;

    // ==================== Generic key-value store ====================

    public final Map<String, String> custom = new HashMap<>();

    // ==================== Convenience accessors ====================

    public String get(String key) { return custom.get(key); }

    public String get(String key, String defaultValue) { return custom.getOrDefault(key, defaultValue); }

    public MasterConfig set(String key, String value) { custom.put(key, value); return this; }

    public int getInt(String key, int defaultValue) {
        String v = custom.get(key);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return defaultValue; }
    }

    public MasterConfig setInt(String key, int value) { custom.put(key, String.valueOf(value)); return this; }

    public boolean getBool(String key, boolean defaultValue) {
        String v = custom.get(key);
        if (v == null) return defaultValue;
        return Boolean.parseBoolean(v);
    }

    public MasterConfig setBool(String key, boolean value) { custom.put(key, String.valueOf(value)); return this; }

    public float getFloat(String key, float defaultValue) {
        String v = custom.get(key);
        if (v == null) return defaultValue;
        try { return Float.parseFloat(v); } catch (NumberFormatException e) { return defaultValue; }
    }

    public MasterConfig setFloat(String key, float value) { custom.put(key, String.valueOf(value)); return this; }

    public MasterConfig remove(String key) { custom.remove(key); return this; }

    public boolean has(String key) { return custom.containsKey(key); }

    public void save() { MasterConfigManager.save(); }
}
