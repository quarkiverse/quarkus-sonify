package io.quarkiverse.sonify.runtime.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.sound.midi.Sequence;

import org.junit.jupiter.api.Test;

/**
 * Without access to the package of the JDK synthesizer, as when nothing exports it: MIDI still plays, with the default
 * instruments of the JDK, and the soundfont is ignored. This test runs in a JVM of its own, without that access.
 */
class MidiNoAccessTest {

    private static final ClassLoader RESOURCES = MidiNoAccessTest.class.getClassLoader();

    private final SonifyEngine engine = SonifyEngine.get();

    @Test
    void midiPlaysWithTheDefaultInstruments() throws Exception {
        assertThat(MidiRenderer.canUseSoundbanks()).isFalse();
        TestConfig config = new TestConfig();
        config.soundfont = "square.sf2";

        Sequence notes = Midi.arpeggio();
        engine.configure(config, RESOURCES, List.of(sounds -> sounds.sound("notes").midi(notes)));

        assertThat(engine.usesSoundfont()).isFalse();
        assertThat(Midi.peak(engine.settings().customs().get("notes").samples())).isGreaterThan(0.01f);
    }
}
