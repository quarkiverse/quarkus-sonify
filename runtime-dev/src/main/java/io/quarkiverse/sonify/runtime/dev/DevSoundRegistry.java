package io.quarkiverse.sonify.runtime.dev;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.sound.midi.Sequence;
import javax.sound.midi.Soundbank;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.jboss.logging.Logger;

import io.quarkiverse.sonify.SoundDefinition;
import io.quarkiverse.sonify.SoundRegistry;
import io.quarkiverse.sonify.Wave;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Clip;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Tone;

/**
 * Collects what the customizers of the application define, in the form the engine plays.
 */
final class DevSoundRegistry implements SoundRegistry {

    private static final Logger LOG = Logger.getLogger(DevSoundRegistry.class);

    private static final int MAX_MS = (int) (SonifyEngine.MAX_CLIP_SECONDS * 1000);

    private final ClassLoader resources;
    private final Soundbank soundbank;
    private final Map<String, Clip> methods = new HashMap<>();
    private final Map<String, Clip> statuses = new HashMap<>();
    private final Map<String, Clip> customs = new HashMap<>();

    /**
     * @param soundbank the instruments of the MIDI sounds, or {@code null} for the default instruments of the JDK
     */
    DevSoundRegistry(ClassLoader resources, Soundbank soundbank) {
        this.resources = resources;
        this.soundbank = soundbank;
    }

    Map<String, Clip> methods() {
        return methods;
    }

    Map<String, Clip> statuses() {
        return statuses;
    }

    Map<String, Clip> customs() {
        return customs;
    }

    @Override
    public SoundDefinition method(String httpMethod) {
        Objects.requireNonNull(httpMethod, "httpMethod");
        return new Definition(methods, httpMethod.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public SoundDefinition status(int statusCode) {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("Not a status code between 100 and 599: " + statusCode);
        }
        return new Definition(statuses, Integer.toString(statusCode));
    }

    @Override
    public SoundDefinition statusClass(int firstDigit) {
        if (firstDigit < 1 || firstDigit > 5) {
            throw new IllegalArgumentException("Not the first digit of a status code, between 1 and 5: " + firstDigit);
        }
        return new Definition(statuses, firstDigit + "xx");
    }

    @Override
    public SoundDefinition sound(String name) {
        Objects.requireNonNull(name, "name");
        return new Definition(customs, name);
    }

    private final class Definition implements SoundDefinition {

        private final Map<String, Clip> target;
        private final String key;
        private final List<Tone> tones = new ArrayList<>();

        Definition(Map<String, Clip> target, String key) {
            this.target = target;
            this.key = key;
        }

        @Override
        public void off() {
            tones.clear();
            target.put(key, Clip.OFF);
        }

        @Override
        public void file(String path) {
            Objects.requireNonNull(path, "path");
            try {
                Clip clip = Clip.of(SonifyEngine.load(path, resources, soundbank));
                tones.clear();
                target.put(key, clip);
            } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException e) {
                LOG.warnf("Sonify ignores the file of the sound '%s': cannot read '%s' (%s)", key, path, e.getMessage());
            }
        }

        @Override
        public void midi(Sequence sequence) {
            Objects.requireNonNull(sequence, "sequence");
            try {
                Clip clip = Clip.of(SonifyEngine.midi(sequence, soundbank));
                tones.clear();
                target.put(key, clip);
            } catch (IOException | IllegalArgumentException e) {
                LOG.warnf("Sonify ignores the MIDI sequence of the sound '%s' (%s)", key, e.getMessage());
            }
        }

        @Override
        public void samples(float[] samples, float sampleRate) {
            Objects.requireNonNull(samples, "samples");
            if (!(sampleRate > 0)) {
                throw new IllegalArgumentException("Not a positive sample rate: " + sampleRate);
            }
            int length = (int) Math.min(samples.length, sampleRate * SonifyEngine.MAX_CLIP_SECONDS);
            double[] mono = new double[length];
            for (int i = 0; i < length; i++) {
                mono[i] = Math.max(-1, Math.min(1, samples[i]));
            }
            tones.clear();
            target.put(key, Clip.of(SonifyEngine.resample(mono, sampleRate)));
        }

        @Override
        public SoundDefinition tone(Wave wave, double frequency, int startMs, int durationMs) {
            Objects.requireNonNull(wave, "wave");
            if (wave != Wave.NOISE && !(frequency >= 20 && frequency <= 20000)) {
                throw new IllegalArgumentException("Not a frequency between 20 and 20000 Hz: " + frequency);
            }
            if (startMs < 0 || durationMs < 1 || startMs + durationMs > MAX_MS) {
                throw new IllegalArgumentException("A tone must start at 0 ms or later, last 1 ms or more and end within "
                        + MAX_MS + " ms: start " + startMs + " ms, duration " + durationMs + " ms");
            }
            tones.add(new Tone(wave, frequency, startMs, durationMs));
            target.put(key, new Clip(null, List.copyOf(tones)));
            return this;
        }
    }
}
