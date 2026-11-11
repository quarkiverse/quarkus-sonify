package io.quarkiverse.sonify.runtime.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.sonify.runtime.dev.MidiRenderer.TimedMessage;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Clip;

/**
 * MIDI files, MIDI sequences and soundfonts. These tests run with the package of the JDK synthesizer exported, as
 * Quarkus does for the application: see {@link MidiNoAccessTest} for the other case.
 */
class MidiTest {

    private static final ClassLoader RESOURCES = MidiTest.class.getClassLoader();
    private static final int MAX_SAMPLES = (int) (44100 * SonifyEngine.MAX_CLIP_SECONDS);

    private final SonifyEngine engine = SonifyEngine.get();

    @TempDir
    Path directory;

    @Test
    void theSynthesizerCanRenderWithASoundbank() {
        assertThat(MidiRenderer.canUseSoundbanks()).isTrue();
    }

    @Test
    void aMidiFileIsASoundFile() throws Exception {
        Path file = Midi.write(Midi.arpeggio(), directory.resolve("arpeggio.mid"));

        float[] samples = SonifyEngine.load(file.toString(), RESOURCES);

        // half a second of notes and the time they ring
        assertThat(samples.length).isBetween(44100 / 2, MAX_SAMPLES);
        assertThat(Midi.peak(samples)).isGreaterThan(0.01f);
    }

    @Test
    void aLongSequenceIsCut() throws Exception {
        Sequence sequence = Midi.arpeggio();
        // one more note, ten seconds later
        Track track = sequence.getTracks()[0];
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 72, 100), 120 * 20));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 72, 0), 120 * 21));

        assertThat(SonifyEngine.midi(sequence, null)).hasSize(MAX_SAMPLES);
    }

    @Test
    void theSoundfontOfTheConfigurationPlaysTheNotes() throws Exception {
        TestConfig config = new TestConfig();
        config.soundfont = "square.sf2";
        engine.configure(config, RESOURCES, List.of());
        assertThat(engine.usesSoundfont()).isTrue();

        Soundbank square = MidiSystem.getSoundbank(RESOURCES.getResource("square.sf2"));
        float[] withSoundfont = SonifyEngine.midi(Midi.arpeggio(), square);
        float[] withDefaultInstruments = SonifyEngine.midi(Midi.arpeggio(), null);

        assertThat(Midi.peak(withSoundfont)).isGreaterThan(0.01f);
        assertThat(Midi.peak(withDefaultInstruments)).isGreaterThan(0.01f);
        assertThat(withSoundfont).isNotEqualTo(withDefaultInstruments);
    }

    @Test
    void customizersAndPropertiesUseTheSoundfont() throws Exception {
        TestConfig config = new TestConfig();
        config.soundfont = "square.sf2";
        config.sound.put("from-file", Midi.write(Midi.arpeggio(), directory.resolve("arpeggio.mid")).toString());

        Sequence notes = Midi.arpeggio();
        engine.configure(config, RESOURCES, List.of(sounds -> sounds.sound("from-code").midi(notes)));

        Soundbank square = MidiSystem.getSoundbank(RESOURCES.getResource("square.sf2"));
        float[] expected = SonifyEngine.midi(Midi.arpeggio(), square);
        Clip fromCode = engine.settings().customs().get("from-code");
        Clip fromFile = engine.settings().customs().get("from-file");
        assertThat(fromCode.samples()).isEqualTo(expected);
        assertThat(fromFile.samples()).isEqualTo(expected);
    }

    @Test
    void aMissingSoundfontMeansTheDefaultInstruments() throws Exception {
        TestConfig config = new TestConfig();
        config.soundfont = directory.resolve("missing.sf2").toString();

        Sequence notes = Midi.arpeggio();
        engine.configure(config, RESOURCES, List.of(sounds -> sounds.sound("notes").midi(notes)));

        assertThat(engine.usesSoundfont()).isFalse();
        assertThat(engine.settings().customs().get("notes").samples()).isEqualTo(SonifyEngine.midi(Midi.arpeggio(), null));
    }

    @Test
    void timingFollowsTheTempo() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 100);
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 100));
        // from the second beat on, a beat lasts a second instead of half a second
        track.add(new MidiEvent(Midi.tempo(1_000_000), 100));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 200));

        List<TimedMessage> messages = MidiRenderer.timedMessages(sequence);

        assertThat(messages).extracting(TimedMessage::micros).containsExactly(500_000L, 1_500_000L);
    }

    @Test
    void timingOfAnSmpteSequence() throws Exception {
        // 25 frames per second, 40 ticks per frame: a millisecond per tick
        Sequence sequence = new Sequence(Sequence.SMPTE_25, 40);
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 250));

        assertThat(MidiRenderer.timedMessages(sequence)).extracting(TimedMessage::micros).containsExactly(250_000L);
    }
}
