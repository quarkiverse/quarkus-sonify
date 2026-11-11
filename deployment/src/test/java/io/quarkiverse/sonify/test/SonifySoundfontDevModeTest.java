package io.quarkiverse.sonify.test;

import static org.hamcrest.Matchers.is;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * In dev mode, MIDI sounds are played with the soundfont of the configuration: Quarkus exports the package of the JDK
 * synthesizer that this needs, from Java 25 on (see {@link Soundfonts}).
 */
public class SonifySoundfontDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SoundsResource.class, OrderService.class, TestSounds.class, CountsResource.class,
                            Soundfonts.class)
                    .addAsResource("square.sf2")
                    .addAsResource(new ByteArrayAsset(arpeggio()), "arpeggio.mid")
                    .addAsResource(new StringAsset("""
                            quarkus.sonify.enabled=false
                            quarkus.sonify.soundfont=square.sf2
                            quarkus.sonify.status."201"=arpeggio.mid
                            """), "application.properties"));

    @Test
    public void midiIsPlayedWithTheSoundfont() {
        RestAssured.get("/q/sonify-test/soundfont").then().statusCode(200).body(is(Soundfonts.expected()));
    }

    @Test
    public void theSoundfontSurvivesALiveReload() {
        devMode.modifyResourceFile("application.properties", properties -> properties + "quarkus.sonify.volume=0.3\n");

        RestAssured.get("/q/sonify-test/soundfont").then().statusCode(200).body(is(Soundfonts.expected()));
    }

    private static byte[] arpeggio() {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, 120);
            Track track = sequence.createTrack();
            int[] notes = { 60, 64, 67 };
            for (int i = 0; i < notes.length; i++) {
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, notes[i], 100), i * 60L));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, notes[i], 0), i * 60L + 120));
            }
            ByteArrayOutputStream file = new ByteArrayOutputStream();
            MidiSystem.write(sequence, 1, file);
            return file.toByteArray();
        } catch (InvalidMidiDataException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
