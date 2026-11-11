package io.quarkiverse.sonify.runtime.dev;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.jboss.logging.Logger;

import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.SonifyTheme;
import io.quarkiverse.sonify.Wave;
import io.quarkiverse.sonify.runtime.SonifyConfig;

/**
 * A tiny software synthesizer: events become short voices that a single daemon thread mixes into the default audio
 * output of the machine.
 * <p>
 * The engine is a JVM-wide singleton that survives dev mode restarts. Nothing is opened until the first sound is
 * requested, and a machine without an audio output only gets one log line.
 */
public final class SonifyEngine implements Runnable {

    private static final Logger LOG = Logger.getLogger(SonifyEngine.class);

    private static final float SAMPLE_RATE = 44100f;
    private static final int BLOCK_FRAMES = 512;
    private static final int MAX_VOICES = 32;
    private static final int ATTACK_SAMPLES = 90;

    /** A sound file is cut after this long, so that a long file cannot pile up under load. */
    static final double MAX_CLIP_SECONDS = 2;
    private static final double CLIP_GAIN = 0.8;

    /** C major pentatonic from C4 upwards: overlapping notes never clash. */
    private static final double[] SCALE = pentatonic(261.63, 12);

    private static final SonifyEngine INSTANCE = new SonifyEngine();

    private final LinkedBlockingQueue<Voice> incoming = new LinkedBlockingQueue<>(256);
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong responses = new AtomicLong();
    private final AtomicLong customs = new AtomicLong();
    private boolean started;
    private volatile boolean unavailable;
    private volatile Settings settings = new Settings(true, 0.4, 350, SonifyTheme.TAPS, Map.of(), Map.of(), Map.of());

    private SonifyEngine() {
    }

    public static SonifyEngine get() {
        return INSTANCE;
    }

    /**
     * Applies the customizers of the application and then its configuration, which wins: called on every start of the
     * application.
     *
     * @param resources the class loader of the application, to find the sound files
     */
    public void configure(SonifyConfig config, ClassLoader resources, List<SonifyCustomizer> customizers) {
        DevSoundRegistry registry = new DevSoundRegistry(resources);
        for (SonifyCustomizer customizer : customizers) {
            try {
                customizer.customize(registry);
            } catch (RuntimeException e) {
                LOG.warnf(e, "Sonify ignores the rest of the customizer %s: it failed", customizer.getClass().getName());
            }
        }
        settings = new Settings(config.enabled(),
                Math.max(0, Math.min(1, config.volume())),
                config.responseGap().toMillis(),
                config.theme(),
                merge(registry.methods(),
                        clips("method", config.method(), resources, key -> key.toUpperCase(Locale.ROOT), key -> true)),
                merge(registry.statuses(),
                        clips("status", config.status(), resources, key -> key.toLowerCase(Locale.ROOT),
                                key -> key.matches("[1-5](xx|\\d\\d)"))),
                merge(registry.customs(), clips("sound", config.sound(), resources, key -> key, key -> true)));
        LOG.debugf("Sonify is configured by %d customizers and the configuration: methods %s, statuses %s, sounds %s",
                customizers.size(), settings.methods().keySet(), settings.statuses().keySet(),
                settings.customs().keySet());
    }

    /** What the configuration says wins over what the customizers say. */
    private static Map<String, Clip> merge(Map<String, Clip> fromCustomizers, Map<String, Clip> fromConfiguration) {
        Map<String, Clip> merged = new HashMap<>(fromCustomizers);
        merged.putAll(fromConfiguration);
        return Map.copyOf(merged);
    }

    /** The application (re)started: a rising three-note chime. */
    public void chime() {
        tone(Wave.SINE, SCALE[7], 0.35, 500, 0, 1);
        tone(Wave.SINE, SCALE[8], 0.35, 500, 110, 1);
        tone(Wave.SINE, SCALE[10], 0.35, 700, 220, 1);
    }

    /**
     * A request arrived. Every HTTP method has its own motif of short taps:
     * <ul>
     * <li>GET: one tap</li>
     * <li>POST: two rising taps</li>
     * <li>PUT: the same tap twice</li>
     * <li>PATCH: the same tap three times</li>
     * <li>DELETE: two falling taps</li>
     * <li>HEAD: one very high tap</li>
     * <li>OPTIONS: one low tap</li>
     * <li>any other method: a click of noise</li>
     * </ul>
     */
    public void request(String method) {
        requests.incrementAndGet();
        Clip clip = settings.methods().get(method);
        if (clip != null) {
            play(clip, 0);
            return;
        }
        switch (method) {
            case "GET" -> tap(9, 0);
            case "POST" -> {
                tap(7, 0);
                tap(9, 70);
            }
            case "PUT" -> {
                tap(8, 0);
                tap(8, 70);
            }
            case "PATCH" -> {
                tap(8, 0);
                tap(8, 60);
                tap(8, 120);
            }
            case "DELETE" -> {
                tap(9, 0);
                tap(7, 70);
            }
            case "HEAD" -> tap(11, 0);
            case "OPTIONS" -> tap(5, 0);
            default -> tone(Wave.NOISE, 0, 0.2, 25, 0, 1);
        }
    }

    /**
     * A response was sent. The class of the status code picks the kind of sound, and the code within the class picks
     * its pitch, so 200, 201 and 204 are the same sound at different pitches:
     * <ul>
     * <li>1xx: a short, quiet, high note</li>
     * <li>2xx: a clear note</li>
     * <li>3xx: a note that glides upwards</li>
     * <li>4xx: a hollow note, one octave lower</li>
     * <li>5xx: a thud with a falling low note</li>
     * </ul>
     * The pitch is the last two digits of the code as a step on the scale. Codes whose last two digits are beyond the
     * scale (412, 429) wrap around and are followed by a quieter echo of themselves.
     * <p>
     * The response sound never starts sooner than the configured gap after the request sound, so that the two are heard
     * apart even when the response took a millisecond. A request slower than the gap is not delayed at all.
     *
     * @param elapsedMs the time since the request arrived
     */
    public void response(int status, double elapsedMs) {
        responses.incrementAndGet();
        Settings current = settings;
        double delayMs = Math.max(0, current.responseGapMs() - elapsedMs);
        int statusClass = status / 100;
        Clip clip = current.statuses().get(Integer.toString(status));
        if (clip == null) {
            clip = current.statuses().get(statusClass + "xx");
        }
        if (clip != null) {
            play(clip, delayMs);
            return;
        }
        int code = Math.floorMod(status, 100);
        double pitch = SCALE[code % SCALE.length];
        if (statusClass == 5) {
            thud(delayMs);
        }
        responseNote(statusClass, pitch, delayMs, 1);
        if (code >= SCALE.length) {
            responseNote(statusClass, pitch, delayMs + 150, 0.5);
        }
    }

    /**
     * A sound the application asked for by name. Without configuration, the name itself picks two notes, so that
     * different names sound different and the same name always sounds the same.
     */
    public void custom(String name) {
        customs.incrementAndGet();
        Clip clip = settings.customs().get(name);
        if (clip != null) {
            play(clip, 0);
            return;
        }
        int hash = name.hashCode();
        int first = 3 + Math.floorMod(hash, 6);
        int second = 3 + Math.floorMod(hash / 6, 6);
        tone(Wave.SINE, SCALE[first], 0.4, 140, 0, 1);
        tone(Wave.SINE, SCALE[first] * 2, 0.12, 140, 0, 1);
        tone(Wave.SINE, SCALE[second], 0.4, 220, 100, 1);
        tone(Wave.SINE, SCALE[second] * 2, 0.12, 220, 100, 1);
    }

    private void responseNote(int statusClass, double pitch, double delayMs, double gain) {
        switch (statusClass) {
            case 1 -> tone(Wave.SINE, pitch * 2, 0.2 * gain, 80, delayMs, 1);
            case 2 -> tone(Wave.SINE, pitch, 0.5 * gain, 220, delayMs, 1);
            case 3 -> tone(Wave.SINE, pitch, 0.45 * gain, 260, delayMs, 1.00004);
            case 4 -> {
                tone(Wave.TRIANGLE, pitch / 2, 0.5 * gain, 320, delayMs, 1);
                tone(Wave.TRIANGLE, pitch, 0.25 * gain, 320, delayMs, 1);
            }
            case 5 -> tone(Wave.TRIANGLE, pitch / 2, 0.5 * gain, 450, delayMs, 0.99997);
            default -> tone(Wave.NOISE, 0, 0.2 * gain, 40, delayMs, 1);
        }
    }

    private void tap(int scaleIndex, double delayMs) {
        if (settings.theme() == SonifyTheme.BELLS) {
            tone(Wave.SINE, SCALE[scaleIndex], 0.3, 70, delayMs, 1);
        } else {
            tone(Wave.TRIANGLE, SCALE[scaleIndex] * 2, 0.3, 45, delayMs, 1);
        }
    }

    private void thud(double delayMs) {
        play(new SynthVoice(Wave.SINE, 120, 0.9, 380, millis(delayMs), 0.99985));
        play(new SynthVoice(Wave.NOISE, 0, 0.3, 60, millis(delayMs), 1));
    }

    /**
     * Plays one tone in the current theme. The bells theme turns every pitched tone into a sine that rings twice as
     * long, with the inharmonic overtone that makes a bell sound like a bell.
     */
    private void tone(Wave wave, double frequency, double amplitude, double durationMs, double delayMs, double drift) {
        int delay = millis(delayMs);
        if (settings.theme() == SonifyTheme.BELLS && wave != Wave.NOISE) {
            play(new SynthVoice(Wave.SINE, frequency, amplitude, durationMs * 2, delay, drift));
            play(new SynthVoice(Wave.SINE, frequency * 2.76, amplitude * 0.25, durationMs * 1.2, delay, drift));
        } else {
            play(new SynthVoice(wave, frequency, amplitude, durationMs, delay, drift));
        }
    }

    /** Plays what the application defined, as it is: the theme does not change it. */
    private void play(Clip clip, double delayMs) {
        if (clip.samples() != null) {
            play(new ClipVoice(clip.samples(), millis(delayMs)));
        }
        for (Tone tone : clip.tones()) {
            play(new SynthVoice(tone.wave(), tone.frequency(), 0.45, tone.durationMs(), millis(delayMs + tone.startMs()),
                    1));
        }
    }

    /** What the last call of {@link #configure} resulted in. */
    Settings settings() {
        return settings;
    }

    public long requestCount() {
        return requests.get();
    }

    public long responseCount() {
        return responses.get();
    }

    public long customCount() {
        return customs.get();
    }

    private void play(Voice voice) {
        if (unavailable || !settings.enabled()) {
            return;
        }
        ensureStarted();
        // a full queue means we are under load: dropping sounds is the right thing to do
        incoming.offer(voice);
    }

    private synchronized void ensureStarted() {
        if (!started) {
            started = true;
            Thread thread = new Thread(this, "sonify-audio");
            thread.setDaemon(true);
            thread.start();
        }
    }

    @Override
    public void run() {
        ensureJavaHome();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, BLOCK_FRAMES * 2 * 8);
            line.start();
            LOG.infof("Sonify is playing on the default audio output (%s)", line.getLineInfo());
            mix(line);
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException | LinkageError e) {
            // a LinkageError: a native executable without the audio library of the JDK, or Linux without ALSA
            unavailable = true;
            incoming.clear();
            LOG.warnf("Sonify is silent: no audio output is available (%s)", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The audio code of the JDK reads its optional {@code conf/sound.properties} from {@code java.home}, and fails when
     * the property is not set at all, which is the case in a native executable. The folder of the executable is as good
     * a home as any: a missing file is ignored.
     */
    private static void ensureJavaHome() {
        if (System.getProperty("java.home") == null) {
            String home = ProcessHandle.current().info().command()
                    .map(command -> Path.of(command).toAbsolutePath().getParent())
                    .map(Path::toString)
                    .orElse(".");
            System.setProperty("java.home", home);
        }
    }

    private void mix(SourceDataLine line) throws InterruptedException {
        List<Voice> voices = new ArrayList<>();
        byte[] buffer = new byte[BLOCK_FRAMES * 2];
        while (true) {
            if (voices.isEmpty()) {
                // nothing to play: sleep until the next event
                voices.add(incoming.take());
            }
            Voice next;
            while ((next = incoming.poll()) != null) {
                if (voices.size() < MAX_VOICES) {
                    voices.add(next);
                }
            }
            double volume = settings.volume();
            for (int i = 0; i < BLOCK_FRAMES; i++) {
                double sample = 0;
                for (int v = 0; v < voices.size(); v++) {
                    sample += voices.get(v).next();
                }
                // tanh keeps a burst of voices from clipping harshly
                int pcm = (int) (Math.tanh(sample * volume) * Short.MAX_VALUE);
                buffer[i * 2] = (byte) pcm;
                buffer[i * 2 + 1] = (byte) (pcm >> 8);
            }
            voices.removeIf(Voice::done);
            line.write(buffer, 0, buffer.length);
        }
    }

    private static Map<String, Clip> clips(String kind, Map<String, String> configured, ClassLoader resources,
            UnaryOperator<String> normalize, Predicate<String> valid) {
        Map<String, Clip> clips = new HashMap<>();
        for (Map.Entry<String, String> entry : configured.entrySet()) {
            String key = normalize.apply(entry.getKey().trim());
            String value = entry.getValue().trim();
            String property = "quarkus.sonify." + kind + ".\"" + entry.getKey() + "\"";
            if (!valid.test(key)) {
                LOG.warnf("Sonify ignores %s: the key is not a status code such as 404 or a class such as 4xx", property);
            } else if (value.equalsIgnoreCase("off")) {
                clips.put(key, Clip.OFF);
            } else {
                try {
                    clips.put(key, Clip.of(load(value, resources)));
                } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException e) {
                    LOG.warnf("Sonify ignores %s: cannot read the sound file '%s' (%s)", property, value, e.getMessage());
                }
            }
        }
        return clips;
    }

    /**
     * Reads a sound file as mono samples at the rate of the engine, cut to {@link #MAX_CLIP_SECONDS}. The file is
     * looked up in the resources of the application first, and then on the file system.
     */
    static float[] load(String path, ClassLoader resources) throws IOException, UnsupportedAudioFileException {
        URL resource = resources.getResource(path);
        InputStream stream;
        if (resource != null) {
            stream = resource.openStream();
        } else if (Files.isRegularFile(Path.of(path))) {
            stream = Files.newInputStream(Path.of(path));
        } else {
            throw new IOException("not found in the resources of the application or on the file system");
        }
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new BufferedInputStream(stream))) {
            AudioFormat format = source.getFormat();
            int channels = format.getChannels();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16, channels,
                    channels * 2, format.getSampleRate(), false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, source)) {
                int maxFrames = (int) (format.getSampleRate() * MAX_CLIP_SECONDS);
                byte[] bytes = converted.readNBytes(maxFrames * pcm.getFrameSize());
                int frames = bytes.length / pcm.getFrameSize();
                double[] mono = new double[frames];
                for (int frame = 0; frame < frames; frame++) {
                    double sum = 0;
                    for (int channel = 0; channel < channels; channel++) {
                        int index = (frame * channels + channel) * 2;
                        sum += (short) ((bytes[index] & 0xFF) | (bytes[index + 1] << 8)) / 32768.0;
                    }
                    mono[frame] = sum / channels;
                }
                return resample(mono, format.getSampleRate());
            }
        }
    }

    static float[] resample(double[] mono, float sourceRate) {
        int length = (int) (mono.length * (double) SAMPLE_RATE / sourceRate);
        float[] samples = new float[length];
        // a short fade at the end, in case the file was cut in the middle of a sound
        int fade = Math.min(length, millis(15));
        for (int i = 0; i < length; i++) {
            double position = i * (double) sourceRate / SAMPLE_RATE;
            int index = (int) position;
            double fraction = position - index;
            double a = mono[Math.min(index, mono.length - 1)];
            double b = mono[Math.min(index + 1, mono.length - 1)];
            double gain = i >= length - fade ? (double) (length - i) / fade : 1;
            samples[i] = (float) ((a + (b - a) * fraction) * gain * CLIP_GAIN);
        }
        return samples;
    }

    private static int millis(double ms) {
        return (int) (ms * SAMPLE_RATE / 1000);
    }

    private static double[] pentatonic(double root, int notes) {
        int[] semitones = { 0, 2, 4, 7, 9 };
        double[] scale = new double[notes];
        for (int i = 0; i < notes; i++) {
            int semitone = 12 * (i / semitones.length) + semitones[i % semitones.length];
            scale[i] = root * Math.pow(2, semitone / 12.0);
        }
        return scale;
    }

    /** The configuration as the engine needs it, replaced as a whole on every start of the application. */
    record Settings(boolean enabled, double volume, double responseGapMs, SonifyTheme theme,
            Map<String, Clip> methods, Map<String, Clip> statuses, Map<String, Clip> customs) {
    }

    /**
     * What the application says to play instead of a built-in sound: the samples of a sound file or of computed audio,
     * some tones, or nothing at all.
     */
    record Clip(float[] samples, List<Tone> tones) {

        static final Clip OFF = new Clip(null, List.of());

        static Clip of(float[] samples) {
            return new Clip(samples, List.of());
        }
    }

    /** One tone of a sound defined by a customizer. */
    record Tone(Wave wave, double frequency, int startMs, int durationMs) {
    }

    /** One sound being played. Only the audio thread touches a voice after it is queued. */
    private interface Voice {

        double next();

        boolean done();
    }

    /** The samples of a sound file. */
    private static final class ClipVoice implements Voice {

        private final float[] samples;
        private int delay;
        private int position;

        ClipVoice(float[] samples, int delaySamples) {
            this.samples = samples;
            this.delay = delaySamples;
        }

        @Override
        public double next() {
            if (delay > 0) {
                delay--;
                return 0;
            }
            return position < samples.length ? samples[position++] : 0;
        }

        @Override
        public boolean done() {
            return delay == 0 && position >= samples.length;
        }
    }

    /** One decaying synthesized sound. */
    private static final class SynthVoice implements Voice {

        private final Wave wave;
        private final double amplitude;
        private final double decay;
        private final double frequencyDrift;
        private double frequency;
        private double phase;
        private double envelope = 1;
        private int delay;
        private int age;
        private long noise = System.nanoTime() | 1;

        SynthVoice(Wave wave, double frequency, double amplitude, double durationMs, int delaySamples,
                double frequencyDrift) {
            this.wave = wave;
            this.frequency = frequency;
            this.amplitude = amplitude;
            // the envelope reaches -60 dB at the end of the duration
            this.decay = Math.pow(0.001, 1.0 / (durationMs / 1000 * SAMPLE_RATE));
            this.delay = delaySamples;
            this.frequencyDrift = frequencyDrift;
        }

        @Override
        public double next() {
            if (delay > 0) {
                delay--;
                return 0;
            }
            // a short attack ramp avoids a click at the start of the sound
            double attack = age < ATTACK_SAMPLES ? (double) age / ATTACK_SAMPLES : 1;
            age++;
            double value = switch (wave) {
                case SINE -> Math.sin(phase);
                case TRIANGLE -> 2 / Math.PI * Math.asin(Math.sin(phase));
                case NOISE -> nextNoise();
            };
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            frequency *= frequencyDrift;
            double out = value * amplitude * envelope * attack;
            envelope *= decay;
            return out;
        }

        @Override
        public boolean done() {
            return delay == 0 && envelope < 0.001;
        }

        private double nextNoise() {
            noise ^= noise << 13;
            noise ^= noise >>> 7;
            noise ^= noise << 17;
            return (noise & 0xFFFF) / 32768.0 - 1;
        }
    }
}
