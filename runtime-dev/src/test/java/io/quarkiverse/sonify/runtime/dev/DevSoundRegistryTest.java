package io.quarkiverse.sonify.runtime.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import io.quarkiverse.sonify.Wave;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Clip;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Tone;

class DevSoundRegistryTest {

    private final DevSoundRegistry registry = new DevSoundRegistry(
            SonifyEngine.Sources.of(DevSoundRegistryTest.class.getClassLoader()));

    @Test
    void tonesAddUp() {
        registry.sound("shipped")
                .tone(Wave.SINE, 392, 0, 150)
                .tone(Wave.TRIANGLE, 523, 120, 300);

        Clip clip = registry.customs().get("shipped");
        assertThat(clip.samples()).isNull();
        assertThat(clip.tones()).containsExactly(
                new Tone(Wave.SINE, 392, 0, 150),
                new Tone(Wave.TRIANGLE, 523, 120, 300));
    }

    @Test
    void keysAreNormalized() {
        registry.method(" post ").off();
        registry.status(404).off();
        registry.statusClass(5).off();

        assertThat(registry.methods()).containsOnlyKeys("POST");
        assertThat(registry.statuses()).containsOnlyKeys("404", "5xx");
        assertThat(registry.statuses().get("5xx")).isSameAs(Clip.OFF);
    }

    @Test
    void offReplacesTones() {
        var definition = registry.sound("quiet");
        definition.tone(Wave.SINE, 440, 0, 100);
        definition.off();

        assertThat(registry.customs().get("quiet")).isSameAs(Clip.OFF);
    }

    @Test
    void samplesAreResampledClampedAndCut() {
        float[] threeSeconds = new float[3 * 8000];
        java.util.Arrays.fill(threeSeconds, 5f);

        registry.sound("computed").samples(threeSeconds, 8000);

        float[] samples = registry.customs().get("computed").samples();
        assertThat(samples.length).isCloseTo((int) (44100 * SonifyEngine.DEFAULT_MAX_SECONDS), within(2));
        assertThat(samples[1000]).isLessThanOrEqualTo(1f);
    }

    @Test
    void aMissingFileIsIgnored() {
        registry.sound("missing").file("no/such/file.wav");

        assertThat(registry.customs()).isEmpty();
    }

    @Test
    void invalidValuesAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> registry.status(99));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.status(600));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.statusClass(0));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.statusClass(6));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.sound("x").tone(Wave.SINE, 5, 0, 100));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.sound("x").tone(Wave.SINE, 440, -1, 100));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.sound("x").tone(Wave.SINE, 440, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.sound("x").tone(Wave.SINE, 440, 1900, 200));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.sound("x").samples(new float[10], 0));
        // noise has no pitch, so its frequency is not checked
        registry.sound("x").tone(Wave.NOISE, 0, 0, 100);
        assertThat(registry.customs().get("x").tones()).hasSize(1);
    }
}
