package io.quarkiverse.sonify.runtime.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import javax.sound.midi.ShortMessage;

import org.junit.jupiter.api.Test;

import io.quarkiverse.sonify.runtime.dev.MidiRenderer.TimedMessage;

class NotesTest {

    /** What the synthesizer receives, readable: "on 60 @0", "off 60 @500000", "program 11 @0". */
    private static List<String> events(String notes) {
        return MidiRenderer.timedMessages(Notes.parse(notes)).stream()
                .map(NotesTest::describe)
                .toList();
    }

    private static String describe(TimedMessage message) {
        ShortMessage midi = (ShortMessage) message.message();
        String what = switch (midi.getCommand()) {
            case ShortMessage.NOTE_ON -> "on " + midi.getData1() + " v" + midi.getData2();
            case ShortMessage.NOTE_OFF -> "off " + midi.getData1();
            case ShortMessage.PROGRAM_CHANGE -> "program " + midi.getData1();
            default -> "command " + midi.getCommand();
        };
        return what + " @" + message.micros();
    }

    @Test
    void notesRestsAndDurations() {
        // at the default 120 quarter notes per minute, a quarter note lasts half a second
        assertThat(events("C4/4 E4/8 r/8 G4/2")).containsExactly(
                "program 0 @0",
                "on 60 v100 @0",
                "off 60 @500000",
                "on 64 v100 @500000",
                "off 64 @750000",
                "on 67 v100 @1000000",
                "off 67 @2000000");
    }

    @Test
    void aNoteWithoutDurationLastsAsLongAsTheOneBefore() {
        assertThat(events("C4/8 D4")).containsExactly(
                "program 0 @0",
                "on 60 v100 @0",
                "off 60 @250000",
                "on 62 v100 @250000",
                "off 62 @500000");
        // and the first one a quarter note
        assertThat(events("C4")).contains("off 60 @500000");
    }

    @Test
    void dotsMakeANoteLonger() {
        assertThat(events("C4/4.")).contains("off 60 @750000");
        assertThat(events("C4/4..")).contains("off 60 @875000");
    }

    @Test
    void aChordIsPlayedTogether() {
        assertThat(events("C4+E4+G4/2")).containsExactly(
                "program 0 @0",
                "on 60 v100 @0",
                "on 64 v100 @0",
                "on 67 v100 @0",
                "off 60 @1000000",
                "off 64 @1000000",
                "off 67 @1000000");
    }

    @Test
    void sharpsFlatsAndOctaves() {
        assertThat(events("Bb3 F#4 a4 c-1 G9"))
                .filteredOn(event -> event.startsWith("on "))
                .extracting(event -> event.split(" ")[1])
                .containsExactly("58", "66", "69", "0", "127");
    }

    @Test
    void settings() {
        // 60 quarter notes per minute: a second each
        assertThat(events("[program=11, tempo=60, velocity=90] A4")).containsExactly(
                "program 11 @0",
                "on 69 v90 @0",
                "off 69 @1000000");
    }

    @Test
    void aRestAtTheEndCounts() {
        assertThat(Notes.parse("C4/4 r/4").getMicrosecondLength()).isEqualTo(1_000_000);
    }

    @Test
    void mistakesAreExplained() {
        assertThatIllegalArgumentException().isThrownBy(() -> Notes.parse("C4 H4"))
                .withMessageContaining("'H4'");
        assertThatIllegalArgumentException().isThrownBy(() -> Notes.parse("C4/3"))
                .withMessageContaining("/4");
        assertThatIllegalArgumentException().isThrownBy(() -> Notes.parse("C10"))
                .withMessageContaining("G9");
        assertThatIllegalArgumentException().isThrownBy(() -> Notes.parse("[tempo=500] C4"))
                .withMessageContaining("tempo between 20 and 400");
        assertThatIllegalArgumentException().isThrownBy(() -> Notes.parse("[colour=1] C4"))
                .withMessageContaining("colour");
        assertThatIllegalArgumentException().isThrownBy(() -> Notes.parse(" "))
                .withMessageContaining("No notes");
        assertThatIllegalArgumentException().isThrownBy(() -> Notes.parse("r/4 r/8"))
                .withMessageContaining("only rests");
    }
}
