package io.quarkiverse.sonify.runtime.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

/**
 * Reads notes written as text, such as {@code [program=11, tempo=140] C5/8 E5/8 G5+C6/4. r/8 G5/2}, into a MIDI
 * sequence:
 * <ul>
 * <li>A note is a letter, an optional sharp ({@code #}) or flat ({@code b}), and an octave: {@code C4} is middle C,
 * {@code A4} is 440 Hz.</li>
 * <li>Notes joined by {@code +} are a chord.</li>
 * <li>{@code r} is a rest.</li>
 * <li>A duration follows a slash: {@code /1} a whole note, {@code /4} a quarter note, up to {@code /64}. A dot makes it
 * half as long again. Without a duration, a note lasts as long as the one before it, and the first one a quarter
 * note.</li>
 * <li>Optional settings come first, in brackets: {@code program}, the General MIDI instrument from 0 to 127 (0, the
 * piano, by default), {@code tempo}, in quarter notes per minute from 20 to 400 (120 by default), and
 * {@code velocity}, how hard the notes are played, from 1 to 127 (100 by default).</li>
 * </ul>
 */
final class Notes {

    private static final int TICKS_PER_QUARTER = 480;
    private static final int TICKS_PER_WHOLE = TICKS_PER_QUARTER * 4;

    private static final Pattern SETTINGS = Pattern.compile("^\\s*\\[([^\\]]*)\\]");
    private static final Pattern SETTING = Pattern.compile("\\s*([a-z]+)\\s*=\\s*(\\d+)\\s*");
    private static final Pattern TOKEN = Pattern.compile(
            "(?<pitches>[rR]|[A-Ga-g][#b]?-?\\d{1,2}(?:\\+[A-Ga-g][#b]?-?\\d{1,2})*)(?:/(?<value>\\d+)(?<dots>\\.*))?");
    // two digits, so that an octave out of range gets a message about the range
    private static final Pattern PITCH = Pattern.compile("([A-Ga-g])([#b]?)(-?\\d{1,2})");
    private static final int[] PITCH_CLASSES = { 9, 11, 0, 2, 4, 5, 7 }; // A B C D E F G

    private Notes() {
    }

    /**
     * @throws IllegalArgumentException if the text is not notes, with what is wrong and where
     */
    static Sequence parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No notes: write notes such as C5/8 E5/8 G5/4");
        }
        int program = 0;
        int tempo = 120;
        int velocity = 100;
        String notes = text;
        Matcher settings = SETTINGS.matcher(text);
        if (settings.find()) {
            for (String setting : settings.group(1).split(",")) {
                if (setting.isBlank()) {
                    continue;
                }
                Matcher matcher = SETTING.matcher(setting);
                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Not a setting: '" + setting.trim()
                            + "' (expected program, tempo or velocity, such as tempo=140)");
                }
                int value = Integer.parseInt(matcher.group(2));
                switch (matcher.group(1)) {
                    case "program" -> program = inRange("program", value, 0, 127);
                    case "tempo" -> tempo = inRange("tempo", value, 20, 400);
                    case "velocity" -> velocity = inRange("velocity", value, 1, 127);
                    default -> throw new IllegalArgumentException("Not a setting: '" + matcher.group(1)
                            + "' (expected program, tempo or velocity)");
                }
            }
            notes = text.substring(settings.end());
        }

        List<Played> played = new ArrayList<>();
        long tick = 0;
        int duration = TICKS_PER_QUARTER;
        for (String token : notes.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            Matcher matcher = TOKEN.matcher(token);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Not a note: '" + token
                        + "' (expected a note such as C4, F#5/8 or Bb3/4., a chord such as C4+E4+G4/2, or a rest such as r/4)");
            }
            if (matcher.group("value") != null) {
                duration = duration(token, Integer.parseInt(matcher.group("value")), matcher.group("dots").length());
            }
            String pitches = matcher.group("pitches");
            if (!pitches.equalsIgnoreCase("r")) {
                for (String pitch : pitches.split("\\+")) {
                    played.add(new Played(key(token, pitch), tick, duration));
                }
            }
            tick += duration;
        }
        if (played.isEmpty()) {
            throw new IllegalArgumentException("No notes, only rests: write notes such as C5/8 E5/8 G5/4");
        }
        return sequence(played, tick, program, tempo, velocity);
    }

    private static int inRange(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Not a " + name + " between " + min + " and " + max + ": " + value);
        }
        return value;
    }

    private static int duration(String token, int value, int dots) {
        if (value < 1 || value > 64 || Integer.bitCount(value) != 1) {
            throw new IllegalArgumentException("Not a note value in '" + token
                    + "': expected /1, /2, /4, /8, /16, /32 or /64");
        }
        int base = TICKS_PER_WHOLE / value;
        int duration = base;
        for (int i = 1; i <= dots; i++) {
            duration += base >> i;
        }
        return duration;
    }

    /** The MIDI key of a note: C4 is 60. */
    private static int key(String token, String pitch) {
        Matcher matcher = PITCH.matcher(pitch);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a note: '" + pitch + "' in '" + token + "'");
        }
        int pitchClass = PITCH_CLASSES[matcher.group(1).toUpperCase(Locale.ROOT).charAt(0) - 'A'];
        int accidental = switch (matcher.group(2)) {
            case "#" -> 1;
            case "b" -> -1;
            default -> 0;
        };
        int key = 12 * (Integer.parseInt(matcher.group(3)) + 1) + pitchClass + accidental;
        if (key < 0 || key > 127) {
            throw new IllegalArgumentException("Not a note MIDI can play: '" + pitch + "' in '" + token
                    + "' (from C-1 to G9)");
        }
        return key;
    }

    private static Sequence sequence(List<Played> played, long end, int program, int tempo, int velocity) {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, TICKS_PER_QUARTER);
            Track track = sequence.createTrack();
            int microsPerQuarter = 60_000_000 / tempo;
            byte[] tempoData = { (byte) (microsPerQuarter >> 16), (byte) (microsPerQuarter >> 8), (byte) microsPerQuarter };
            track.add(new MidiEvent(new MetaMessage(0x51, tempoData, 3), 0));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, program, 0), 0));
            for (Played note : played) {
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, note.key(), velocity), note.tick()));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, note.key(), 0),
                        note.tick() + note.duration()));
            }
            // a rest at the end lasts too
            track.add(new MidiEvent(new MetaMessage(0x2F, new byte[0], 0), end));
            return sequence;
        } catch (InvalidMidiDataException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private record Played(int key, long tick, int duration) {
    }
}
