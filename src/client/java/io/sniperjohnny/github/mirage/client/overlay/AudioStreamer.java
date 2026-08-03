package io.sniperjohnny.github.mirage.client.overlay;

import io.sniperjohnny.github.mirage.Mirage;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streams raw PCM audio from an InputStream to the system audio output
 * using Java Sound API. Runs on a dedicated thread.
 */
public class AudioStreamer implements Runnable {
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS = 2;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int BUFFER_SIZE = 8192;

    /** Bytes per second of the PCM stream (44100 × 2ch × 2 bytes). */
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * (SAMPLE_SIZE_BITS / 8);

    private final InputStream audioStream;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private volatile float volume = 1.0f;
    private volatile SourceDataLine line;
    private Thread thread;

    /** True once audio has actually started flowing to the output line. */
    private volatile boolean active = false;
    /** True when the input stream ended naturally (EOF — audio finished). */
    private volatile boolean eof = false;
    /** Total PCM bytes handed to the output line (drives the position fallback). */
    private volatile long totalBytesWritten = 0;

    public AudioStreamer(InputStream audioStream) {
        this.audioStream = audioStream;
    }

    /** Start playback on a background thread. */
    public void start() {
        thread = new Thread(this, "Overlay-Audio-" + hashCode());
        thread.setDaemon(true);
        thread.start();
    }

    /** Pause or resume audio output. */
    public void setPaused(boolean pause) {
        paused.set(pause);
        SourceDataLine localLine = line;
        if (localLine != null && pause) {
            try {
                localLine.flush();
            } catch (IllegalStateException ignored) {
                // Line was closed concurrently (stop()/run() finally) — safe to ignore.
            }
        }
    }

    /** Set volume (0.0 – 1.0), multiplied by Minecraft's master volume. */
    public void setVolume(float vol) {
        float clamped = Math.max(0f, Math.min(1f, vol));
        // Skip the control lookup when nothing changed (called every frame).
        if (clamped == this.volume) return;
        this.volume = clamped;
        applyVolume();
    }

    private void applyVolume() {
        SourceDataLine localLine = line;
        if (localLine != null && localLine.isOpen()) {
            try {
                // Read Minecraft's master volume and multiply
                double mcVol = net.minecraft.client.Minecraft.getInstance().options.getSoundSourceVolume(
                        net.minecraft.sounds.SoundSource.MASTER);
                float effective = (float) (this.volume * mcVol);
                FloatControl ctrl = (FloatControl) localLine.getControl(FloatControl.Type.MASTER_GAIN);
                float dB = 20f * (float) Math.log10(Math.max(effective, 0.0001f));
                dB = Math.max(ctrl.getMinimum(), Math.min(dB, ctrl.getMaximum()));
                ctrl.setValue(dB);
            } catch (Exception ignored) {}
        }
    }

    /**
     * @return true once audio has started flowing to the output line.
     *         Used by the video frame reader to know when the audio clock is live.
     */
    public boolean isActive() { return active; }

    /** @return true when the audio stream reached its end (EOF). */
    public boolean isEof() { return eof; }

    /**
     * Audible playback position in seconds, used as the master clock for
     * video/audio sync. Based on the frames the audio line has actually played
     * (falls back to bytes-written minus the line buffer when the frame
     * position counter is unavailable).
     */
    public double getPlaybackPositionSeconds() {
        SourceDataLine localLine = line;
        if (localLine == null || !localLine.isOpen()) return 0;
        try {
            long frames = localLine.getLongFramePosition();
            if (frames > 0) return frames / (double) SAMPLE_RATE;
        } catch (Exception ignored) {
            // frame-position counter unsupported on this backend — use fallback
        }
        double written = totalBytesWritten / (double) BYTES_PER_SECOND;
        double buffered = localLine.getBufferSize() / (double) BYTES_PER_SECOND;
        return Math.max(0, written - buffered);
    }

    /** Stop and clean up. */
    public void stop() {
        running.set(false);
        active = false;
        if (thread != null) thread.interrupt();
        SourceDataLine localLine = line;
        if (localLine != null) {
            try {
                localLine.stop();
            } catch (Exception ignored) {}
            try {
                localLine.close();
            } catch (Exception ignored) {}
            line = null;
        }
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);
        SourceDataLine newLine;
        try {
            newLine = AudioSystem.getSourceDataLine(format);
            newLine.open(format, BUFFER_SIZE * 4);
        } catch (LineUnavailableException e) {
            Mirage.LOGGER.error("Audio line unavailable", e);
            try { audioStream.close(); } catch (IOException ignored) {}
            return;
        }
        if (!running.get()) {
            try { newLine.close(); } catch (Exception ignored) {}
            try { audioStream.close(); } catch (IOException ignored) {}
            return;
        }
        line = newLine;
        try {
            applyVolume();
            newLine.start();
        } catch (IllegalStateException e) {
            try { audioStream.close(); } catch (IOException ignored) {}
            return;
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            while (running.get()) {
                if (paused.get()) {
                    Thread.sleep(50);
                    continue;
                }
                int bytesRead = audioStream.read(buffer);
                if (bytesRead == -1) { eof = true; break; }
                SourceDataLine localLine = line;
                if (localLine == null || !running.get()) break;
                localLine.write(buffer, 0, bytesRead);
                totalBytesWritten += bytesRead;
                active = true;
            }
        } catch (IOException e) {
            if (running.get()) {
                Mirage.LOGGER.warn("Audio stream error: {}", e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IllegalStateException e) {
            // Line was closed by stop() while write() was in flight — expected
        } finally {
            SourceDataLine localLine = line;
            if (localLine != null) {
                try {
                    localLine.drain();
                } catch (Exception ignored) {}
                try {
                    localLine.close();
                } catch (Exception ignored) {}
                line = null;
            }
            try { audioStream.close(); } catch (IOException ignored) {}
        }
    }
}
