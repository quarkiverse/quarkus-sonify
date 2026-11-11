package io.quarkiverse.sonify.it.production;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.SoundRegistry;
import io.quarkiverse.sonify.Wave;

@ApplicationScoped
public class ProductionSounds implements SonifyCustomizer {

    private volatile boolean called;

    @Override
    public void customize(SoundRegistry sounds) {
        called = true;
        sounds.sound("audit").tone(Wave.SINE, 440, 0, 100);
        sounds.sound("from-code").midi(chord());
    }

    /** C major, as MIDI notes played together. */
    private static Sequence chord() {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, 120);
            Track track = sequence.createTrack();
            for (int note : new int[] { 60, 64, 67 }) {
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, note, 90), 0));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), 240));
            }
            return sequence;
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean called() {
        return called;
    }
}
