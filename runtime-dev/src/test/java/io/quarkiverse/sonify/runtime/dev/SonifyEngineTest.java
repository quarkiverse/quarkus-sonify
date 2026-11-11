package io.quarkiverse.sonify.runtime.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.Wave;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Clip;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Settings;

class SonifyEngineTest {

    private static final ClassLoader RESOURCES = SonifyEngineTest.class.getClassLoader();

    private final SonifyEngine engine = SonifyEngine.get();

    @TempDir
    Path directory;

    @Test
    void resampleConvertsToTheRateOfTheEngine() {
        double[] halfSecondAt22050 = new double[11025];
        java.util.Arrays.fill(halfSecondAt22050, 0.5);

        float[] samples = SonifyEngine.resample(halfSecondAt22050, 22050);

        assertThat(samples).hasSize(22050);
        // the clip gain is applied
        assertThat(samples[100]).isCloseTo(0.4f, within(0.0001f));
        // the end fades out, in case the sound was cut
        assertThat(samples[samples.length - 1]).isLessThan(0.01f);
    }

    @Test
    void loadReadsAStereoFileAsMono() throws Exception {
        Path file = wav("stereo.wav", 8000, 2, 0.25);

        float[] samples = SonifyEngine.load(file.toString(), RESOURCES);

        // a quarter of a second, whatever the rate and the channels of the file
        assertThat(samples.length).isCloseTo(11025, within(2));
    }

    @Test
    void loadCutsALongFile() throws Exception {
        Path file = wav("long.wav", 8000, 1, 3);

        float[] samples = SonifyEngine.load(file.toString(), RESOURCES);

        assertThat(samples.length).isCloseTo((int) (44100 * SonifyEngine.DEFAULT_MAX_SECONDS), within(2));
    }

    @Test
    void loadFailsForAMissingFile() {
        assertThatThrownBy(() -> SonifyEngine.load(directory.resolve("missing.wav").toString(), RESOURCES))
                .isInstanceOf(IOException.class);
    }

    @Test
    void configurationIsNormalizedAndValidated() throws Exception {
        TestConfig config = new TestConfig();
        config.volume = 7;
        config.method.put("options", "off");
        config.status.put("5XX", "OFF");
        config.status.put("418", wav("whistle.wav", 8000, 1, 0.1).toString());
        config.status.put("teapot", "off");
        config.status.put("404", directory.resolve("missing.wav").toString());
        config.sound.put("order-placed", "off");

        engine.configure(config, RESOURCES, List.of());

        Settings settings = engine.settings();
        assertThat(settings.volume()).isEqualTo(1);
        assertThat(settings.responseGapMs()).isEqualTo(350);
        assertThat(settings.methods()).containsOnlyKeys("OPTIONS");
        assertThat(settings.methods().get("OPTIONS")).isSameAs(Clip.OFF);
        // an invalid key and an unreadable file are ignored, not fatal
        assertThat(settings.statuses()).containsOnlyKeys("5xx", "418");
        assertThat(settings.statuses().get("418").samples()).isNotEmpty();
        assertThat(settings.customs()).containsOnlyKeys("order-placed");
    }

    @Test
    void configurationWinsOverCustomizers() {
        TestConfig config = new TestConfig();
        config.sound.put("order-placed", "off");
        SonifyCustomizer customizer = sounds -> {
            sounds.sound("order-placed").tone(Wave.SINE, 440, 0, 100);
            sounds.sound("shipped").tone(Wave.SINE, 440, 0, 100);
            sounds.status(409).off();
        };

        engine.configure(config, RESOURCES, List.of(customizer));

        Settings settings = engine.settings();
        assertThat(settings.customs()).containsOnlyKeys("order-placed", "shipped");
        assertThat(settings.customs().get("order-placed")).isSameAs(Clip.OFF);
        assertThat(settings.customs().get("shipped").tones()).hasSize(1);
        assertThat(settings.statuses()).containsOnlyKeys("409");
    }

    @Test
    void aFailingCustomizerDoesNotStopTheOthers() {
        SonifyCustomizer failing = sounds -> {
            sounds.sound("before").off();
            sounds.status(42);
        };
        SonifyCustomizer working = sounds -> sounds.sound("after").off();

        engine.configure(new TestConfig(), RESOURCES, List.of(failing, working));

        assertThat(engine.settings().customs()).containsOnlyKeys("before", "after");
    }

    @Test
    void everyEventIsCounted() {
        engine.configure(new TestConfig(), RESOURCES, List.of());
        long requests = engine.requestCount();
        long responses = engine.responseCount();
        long customs = engine.customCount();

        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE")) {
            engine.request(method);
        }
        for (int status : List.of(100, 200, 204, 301, 404, 429, 500, 503, 999)) {
            engine.response(status, 1);
        }
        engine.custom("audit");
        engine.chime();

        assertThat(engine.requestCount() - requests).isEqualTo(8);
        assertThat(engine.responseCount() - responses).isEqualTo(9);
        assertThat(engine.customCount() - customs).isEqualTo(1);
    }

    /** Writes a 16-bit WAV file of a 440 Hz sine. */
    private Path wav(String name, float rate, int channels, double seconds) throws IOException {
        int frames = (int) (rate * seconds);
        byte[] bytes = new byte[frames * channels * 2];
        for (int frame = 0; frame < frames; frame++) {
            short value = (short) (Math.sin(2 * Math.PI * 440 * frame / rate) * 12000);
            for (int channel = 0; channel < channels; channel++) {
                int index = (frame * channels + channel) * 2;
                bytes[index] = (byte) value;
                bytes[index + 1] = (byte) (value >> 8);
            }
        }
        AudioFormat format = new AudioFormat(rate, 16, channels, true, false);
        Path file = directory.resolve(name);
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(bytes), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }
}
