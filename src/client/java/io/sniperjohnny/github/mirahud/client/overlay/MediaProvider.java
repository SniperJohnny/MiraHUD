package io.sniperjohnny.github.mirahud.client.overlay;

import net.minecraft.resources.Identifier;

public interface MediaProvider {
    Identifier getTextureId();

    boolean hasTexture();

    String getCurrentSource();

    int getOriginalWidth();

    int getOriginalHeight();

    boolean updateSource(String source);

    void cleanup();

    boolean isVideo();

    void tick();

    void playPause(boolean play);

    void setVolume(float volume);

    boolean isPlaying();

    default boolean isAlive() {
        return true;
    }

    default void seek(double seconds) {}

    default void restart() {}

    default double getPlaybackPositionSeconds() { return 0; }

    default double getDurationSeconds() { return 0; }
}
