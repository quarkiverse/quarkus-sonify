package io.quarkiverse.sonify.runtime.dev;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Turns MIDI into samples that the engine mixes like any other sound.
 * <p>
 * The synthesizer of the JDK plays MIDI with the instruments of a soundbank. Rendering it into samples, instead of
 * letting it play on an audio line of its own, needs its {@code AudioSynthesizer} interface, which the JDK does not
 * export: the extension asks Quarkus to export it to the application. Without that access, MIDI is still rendered,
 * through the public API, but always with the default instruments of the JDK.
 */
final class MidiRenderer {

    private static final String AUDIO_SYNTHESIZER = "com.sun.media.sound.AudioSynthesizer";

    private static final float SAMPLE_RATE = 44100f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    /** Notes keep ringing after their end: this much of it is kept after the last event. */
    private static final long RELEASE_MICROS = 600_000;

    /** The default tempo of MIDI: 120 beats per minute. */
    private static final int DEFAULT_MICROS_PER_QUARTER = 500_000;

    private static final int TEMPO = 0x51;

    private MidiRenderer() {
    }

    /**
     * Whether MIDI can be rendered with a soundbank of the application, which needs access to the internal
     * {@code AudioSynthesizer} interface of the JDK.
     */
    static boolean canUseSoundbanks() {
        return openStream() != null;
    }

    /**
     * Renders a sequence into mono samples, at most {@code maxSeconds} long.
     *
     * @param soundbank the instruments to play with, or {@code null} for the default instruments of the JDK
     */
    static Audio render(Sequence sequence, Soundbank soundbank, double maxSeconds)
            throws MidiUnavailableException, IOException {
        long micros = Math.min(sequence.getMicrosecondLength() + RELEASE_MICROS, (long) (maxSeconds * 1_000_000));
        int frames = (int) (micros * SAMPLE_RATE / 1_000_000);
        Method openStream = openStream();
        if (openStream == null) {
            return renderWithDefaultInstruments(sequence, frames);
        }
        Synthesizer synthesizer = MidiSystem.getSynthesizer();
        if (!openStream.getDeclaringClass().isInstance(synthesizer)) {
            return renderWithDefaultInstruments(sequence, frames);
        }
        AudioInputStream stream;
        try {
            stream = (AudioInputStream) openStream.invoke(synthesizer, FORMAT, null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return renderWithDefaultInstruments(sequence, frames);
        }
        try (stream) {
            if (soundbank != null) {
                Soundbank defaultSoundbank = synthesizer.getDefaultSoundbank();
                if (defaultSoundbank != null) {
                    synthesizer.unloadAllInstruments(defaultSoundbank);
                }
                if (!synthesizer.isSoundbankSupported(soundbank) || !synthesizer.loadAllInstruments(soundbank)) {
                    throw new IllegalArgumentException("the synthesizer cannot load the soundbank " + soundbank.getName());
                }
            }
            Receiver receiver = synthesizer.getReceiver();
            for (TimedMessage message : timedMessages(sequence)) {
                receiver.send(message.message(), message.micros());
            }
            return new Audio(mono(stream.readNBytes(frames * 2)), SAMPLE_RATE);
        } finally {
            synthesizer.close();
        }
    }

    /** The public way: the JDK reads a MIDI file as audio, with its default instruments. */
    private static Audio renderWithDefaultInstruments(Sequence sequence, int frames) throws IOException {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        int[] types = MidiSystem.getMidiFileTypes(sequence);
        if (types.length == 0) {
            throw new IOException("the sequence cannot be written as a MIDI file");
        }
        MidiSystem.write(sequence, types[types.length - 1], file);
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(file.toByteArray()));
                AudioInputStream converted = AudioSystem.getAudioInputStream(
                        new AudioFormat(source.getFormat().getSampleRate(), 16, source.getFormat().getChannels(), true,
                                false),
                        source)) {
            AudioFormat format = converted.getFormat();
            int channels = format.getChannels();
            byte[] bytes = converted.readNBytes((int) ((long) frames * format.getSampleRate() / SAMPLE_RATE)
                    * format.getFrameSize());
            double[] mono = new double[bytes.length / format.getFrameSize()];
            for (int frame = 0; frame < mono.length; frame++) {
                double sum = 0;
                for (int channel = 0; channel < channels; channel++) {
                    int index = (frame * channels + channel) * 2;
                    sum += (short) ((bytes[index] & 0xFF) | (bytes[index + 1] << 8)) / 32768.0;
                }
                mono[frame] = sum / channels;
            }
            return new Audio(mono, format.getSampleRate());
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("the JDK cannot render MIDI", e);
        }
    }

    /**
     * The messages of all the tracks, in order, with their time in microseconds. Tempo changes are followed; meta
     * messages are not sent to the synthesizer.
     */
    static List<TimedMessage> timedMessages(Sequence sequence) {
        List<MidiEvent> events = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                events.add(track.get(i));
            }
        }
        // stable: events at the same tick keep the order of their tracks
        events.sort(Comparator.comparingLong(MidiEvent::getTick));

        List<TimedMessage> messages = new ArrayList<>();
        boolean ppq = sequence.getDivisionType() == Sequence.PPQ;
        double resolution = sequence.getResolution();
        long micros = 0;
        long lastTick = 0;
        double microsPerQuarter = DEFAULT_MICROS_PER_QUARTER;
        for (MidiEvent event : events) {
            long tick = event.getTick();
            if (ppq) {
                micros += Math.round((tick - lastTick) * microsPerQuarter / resolution);
            } else {
                micros = Math.round(tick * 1_000_000 / (sequence.getDivisionType() * resolution));
            }
            lastTick = tick;
            MidiMessage message = event.getMessage();
            if (message instanceof MetaMessage meta) {
                if (meta.getType() == TEMPO && meta.getData().length == 3) {
                    byte[] data = meta.getData();
                    microsPerQuarter = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                }
            } else {
                messages.add(new TimedMessage(message, micros));
            }
        }
        return messages;
    }

    /** Reads a MIDI file. */
    static Sequence sequence(InputStream stream) throws IOException {
        try {
            return MidiSystem.getSequence(stream);
        } catch (InvalidMidiDataException e) {
            throw new IOException("not a valid MIDI file: " + e.getMessage(), e);
        }
    }

    private static Method openStream() {
        try {
            Class<?> audioSynthesizer = Class.forName(AUDIO_SYNTHESIZER, false, Synthesizer.class.getClassLoader());
            if (!audioSynthesizer.getModule().isExported(audioSynthesizer.getPackageName(),
                    MidiRenderer.class.getModule())) {
                return null;
            }
            return audioSynthesizer.getMethod("openStream", AudioFormat.class, Map.class);
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException e) {
            return null;
        }
    }

    private static double[] mono(byte[] bytes) {
        double[] mono = new double[bytes.length / 2];
        for (int i = 0; i < mono.length; i++) {
            mono[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8)) / 32768.0;
        }
        return mono;
    }

    record TimedMessage(MidiMessage message, long micros) {
    }

    /** Mono samples between -1 and 1, and their rate. */
    record Audio(double[] mono, float sampleRate) {
    }
}
