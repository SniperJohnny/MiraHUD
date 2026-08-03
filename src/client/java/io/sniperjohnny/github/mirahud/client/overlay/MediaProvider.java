package io.sniperjohnny.github.mirahud.client.overlay;

import net.minecraft.resources.Identifier;

/**
 * Abstraction over a media source (image, video, URL stream) that provides
 * a texture for HUD rendering. Implementations handle loading, decoding,
 * audio playback, and cleanup of their specific media type.
 */
public interface MediaProvider {
    /** The registered OpenGL texture identifier. */
    Identifier getTextureId();

    /** Whether a valid texture is currently loaded. */
    boolean hasTexture();

    /** The source path/URL currently loaded, or empty string. */
    String getCurrentSource();

    /** Original width of the loaded media in pixels. */
    int getOriginalWidth();

    /** Original height of the loaded media in pixels. */
    int getOriginalHeight();

    /**
     * Load or reload a new source.
     * @param source file path or URL
     * @return true on success
     */
    boolean updateSource(String source);

    /** Release all GPU and memory resources (including audio). */
    void cleanup();

    // ---- Video / streaming extensions ----

    /** @return true if this provider is a video or stream that needs per-frame ticking. */
    boolean isVideo();

    /**
     * Called every render frame. For video providers this decodes and uploads
     * the next frame and manages audio sync. No-op for static images.
     */
    void tick();

    /** Toggle play/pause. No-op for images. */
    void playPause(boolean play);

    /** Set playback volume (0.0 – 1.0). No-op for images. */
    void setVolume(float volume);

    /** @return true if currently playing. */
    boolean isPlaying();

    /**
     * @return true if the provider's underlying pipeline is still functional
     *         (e.g. the FFmpeg process is alive). Used by the render loop to
     *         detect crashed video pipelines and restart them.
     */
    default boolean isAlive() {
        return true;
    }

    /**
     * Seek to a specific position in seconds. No-op for images.
     * @param seconds target playback position (clamped to >= 0)
     */
    default void seek(double seconds) {}

    /** Restart playback from the beginning. No-op for images. */
    default void restart() {}

    /**
     * @return current playback position in seconds, or 0 if not applicable.
     */
    default double getPlaybackPositionSeconds() { return 0; }
}
