package io.quarkiverse.sonify.demo;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.SoundRegistry;
import io.quarkiverse.sonify.Wave;

/**
 * Sounds defined in code. Only called in dev mode: a production build removes this bean as unused.
 */
@ApplicationScoped
public class DemoSounds implements SonifyCustomizer {

    @Override
    public void customize(SoundRegistry sounds) {
        // a custom sound made of three rising tones
        sounds.sound("shipped")
                .tone(Wave.SINE, 392, 0, 150)
                .tone(Wave.SINE, 523, 120, 150)
                .tone(Wave.SINE, 659, 240, 400);

        // a status code with its own sound: two flat knocks for 409 Conflict
        sounds.status(409)
                .tone(Wave.TRIANGLE, 220, 0, 120)
                .tone(Wave.TRIANGLE, 220, 160, 120);

        // computed audio: a short downward sweep
        float[] sweep = new float[8000];
        double phase = 0;
        for (int i = 0; i < sweep.length; i++) {
            phase += 2 * Math.PI * (1200 - 900.0 * i / sweep.length) / 16000;
            sweep[i] = (float) (Math.sin(phase) * 0.6 * (1 - (double) i / sweep.length));
        }
        sounds.status(410).samples(sweep, 16000);

        // MIDI notes: a chord on a vibraphone for 204 No Content
        sounds.status(204).midi(vibraphoneChord());

        // application.properties also defines "order-placed", and a property wins: this line has no effect
        sounds.sound("order-placed").off();
    }

    private static Sequence vibraphoneChord() {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, 120);
            Track track = sequence.createTrack();
            // program 11 is the vibraphone in General MIDI
            track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, 11, 0), 0));
            for (int note : new int[] { 65, 69, 72 }) {
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, note, 90), 0));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), 240));
            }
            return sequence;
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        }
    }
}
