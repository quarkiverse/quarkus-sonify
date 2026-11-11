package io.quarkiverse.sonify.runtime.dev;

import java.io.IOException;
import java.nio.file.Path;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/** MIDI for the tests. */
final class Midi {

    private Midi() {
    }

    /** Three rising notes on program 0, at the default tempo: about half a second. */
    static Sequence arpeggio() throws InvalidMidiDataException {
        Sequence sequence = new Sequence(Sequence.PPQ, 120);
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 0, 0), 0));
        int[] notes = { 60, 64, 67 };
        for (int i = 0; i < notes.length; i++) {
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, notes[i], 100), i * 60L));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, notes[i], 0), i * 60L + 120));
        }
        return sequence;
    }

    /** A tempo change, as the meta message of a MIDI file. */
    static MetaMessage tempo(int microsPerQuarter) throws InvalidMidiDataException {
        byte[] data = { (byte) (microsPerQuarter >> 16), (byte) (microsPerQuarter >> 8), (byte) microsPerQuarter };
        return new MetaMessage(0x51, data, 3);
    }

    static Path write(Sequence sequence, Path file) throws IOException {
        MidiSystem.write(sequence, 1, file.toFile());
        return file;
    }

    static float peak(float[] samples) {
        float peak = 0;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }
}
