package io.quarkiverse.sonify;

import javax.sound.midi.Sequence;

/**
 * What one sound is. The last call wins, except for {@link #tone(Wave, double, int, int)}, which adds up: several tones
 * make a motif or a chord.
 */
public interface SoundDefinition {

    /**
     * No sound at all.
     */
    void off();

    /**
     * A sound file, looked up in the resources of the application first and then on the file system: a WAV file, or a
     * MIDI file, which is played with the instruments of {@code quarkus.sonify.soundfont}. At most
     * {@code quarkus.sonify.max-sound-length} of it is played. A file that cannot be read is logged and leaves the sound as
     * it was.
     */
    void file(String path);

    /**
     * Notes, as a MIDI sequence: instruments, pitches, velocities and timing, built with the {@code javax.sound.midi} API
     * of the JDK. They are played with the instruments of {@code quarkus.sonify.soundfont}, or the default instruments
     * of the JDK. At most {@code quarkus.sonify.max-sound-length} of it is played. A sequence that cannot be played is
     * logged and leaves the sound as it was.
     */
    void midi(Sequence sequence);

    /**
     * Notes, written as text: {@code "C5/8 E5/8 G5/4"}. A note is a letter, an optional sharp ({@code #}) or flat
     * ({@code b}) and an octave ({@code C4} is middle C), then an optional duration ({@code /4} is a quarter note, a dot
     * makes it half as long again). Notes joined by {@code +} are a chord, and {@code r} is a rest. Settings can come
     * first: {@code "[program=11, tempo=140, velocity=90] C5/8 E5/8 G5/4"}, with the General MIDI instrument, the
     * quarter notes per minute and how hard the notes are played.
     * <p>
     * The notes are played as MIDI, with the instruments of {@code quarkus.sonify.soundfont} or the default instruments
     * of the JDK. The same text works in the configuration, after {@code notes:}.
     *
     * @throws IllegalArgumentException if the text is not notes: the message says what is wrong and where
     */
    void notes(String notes);

    /**
     * Audio computed by the application: mono samples between -1 and 1. At most
     * {@code quarkus.sonify.max-sound-length} of it is played.
     *
     * @param sampleRate the number of samples per second, such as 44100
     * @throws IllegalArgumentException if the sample rate is not positive
     */
    void samples(float[] samples, float sampleRate);

    /**
     * Adds one synthesized tone that fades out over its duration.
     *
     * @param frequency the pitch in hertz, between 20 and 20000 (ignored for {@link Wave#NOISE})
     * @param startMs when the tone starts, in milliseconds after the start of the sound: tones with the same start
     *        make a chord
     * @param durationMs how long the tone rings, in milliseconds
     * @return this definition, to add more tones
     * @throws IllegalArgumentException if a value is out of range, or the tone ends later than
     *         {@code quarkus.sonify.max-sound-length}
     */
    SoundDefinition tone(Wave wave, double frequency, int startMs, int durationMs);
}
