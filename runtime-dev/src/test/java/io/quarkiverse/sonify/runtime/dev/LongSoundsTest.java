package io.quarkiverse.sonify.runtime.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.sonify.Wave;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Clip;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.ClipVoice;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Playing;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine.Sources;

/** Notes in the configuration, the longest a sound may last, and long sounds that do not pile up. */
class LongSoundsTest {

    private static final ClassLoader RESOURCES = LongSoundsTest.class.getClassLoader();

    private final SonifyEngine engine = SonifyEngine.get();

    @TempDir
    Path directory;

    @Test
    void notesInTheConfiguration() {
        TestConfig config = new TestConfig();
        config.status.put("201", "notes:C5/16 E5/16 G5/16 C6/8");
        config.status.put("500", "NOTES: [program=57, tempo=90] G3/4 D3/2");
        config.status.put("404", "notes:C4 H4");

        engine.configure(config, RESOURCES, List.of());

        // the mistake is logged and ignored
        assertThat(engine.settings().statuses()).containsOnlyKeys("201", "500");
        assertThat(Midi.peak(engine.settings().statuses().get("201").samples())).isGreaterThan(0.01f);
    }

    @Test
    void notesInCode() {
        DevSoundRegistry registry = new DevSoundRegistry(Sources.of(RESOURCES));

        registry.sound("fanfare").notes("C5/16 E5/16 G5/16 C6/8");

        assertThat(registry.customs().get("fanfare").samples()).isNotEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> registry.sound("broken").notes("C4 H4"));
    }

    @Test
    void theLongestASoundMayLastIsConfigured() throws Exception {
        TestConfig config = new TestConfig();
        config.maxSoundLength = Duration.ofSeconds(5);
        config.sound.put("long", wav(8).toString());
        // eight seconds of notes
        config.sound.put("song", "notes:[tempo=60] C4/1 E4/1");

        engine.configure(config, RESOURCES, List.of());

        assertThat(engine.settings().customs().get("long").samples().length).isCloseTo(44100 * 5, within(2));
        assertThat(engine.settings().customs().get("song").samples().length).isCloseTo(44100 * 5, within(2));
    }

    @Test
    void tonesAndSamplesFollowTheLongestASoundMayLast() {
        DevSoundRegistry registry = new DevSoundRegistry(new Sources(RESOURCES, null, 5));

        registry.sound("late").tone(Wave.SINE, 440, 4000, 500);
        registry.sound("samples").samples(new float[8000 * 8], 8000);

        assertThat(registry.customs().get("late").tones()).hasSize(1);
        assertThat(registry.customs().get("samples").samples().length).isCloseTo(44100 * 5, within(2));
        assertThatIllegalArgumentException().isThrownBy(() -> registry.sound("too-late").tone(Wave.SINE, 440, 4800, 500));
    }

    @Test
    void theLongestASoundMayLastHasLimits() throws Exception {
        TestConfig config = new TestConfig();
        config.maxSoundLength = Duration.ofMinutes(1);
        config.sound.put("long", wav(12).toString());

        engine.configure(config, RESOURCES, List.of());

        assertThat(engine.settings().customs().get("long").samples().length)
                .isCloseTo((int) (44100 * SonifyEngine.LONGEST_SECONDS), within(2));
    }

    @Test
    void aSoundThatIsStillPlayingIsNotStartedAgain() {
        Clip song = Clip.of(new float[100]);
        Clip other = Clip.of(new float[100]);
        Playing first = new Playing(song);
        List<ClipVoice> playing = List.of(new ClipVoice(song.samples(), 0, first));

        // the same sound, asked for again
        assertThat(SonifyEngine.alreadyPlaying(playing, new ClipVoice(song.samples(), 0, new Playing(song)))).isTrue();
        // another voice of the same playing, such as the second tone of a motif
        assertThat(SonifyEngine.alreadyPlaying(playing, new ClipVoice(song.samples(), 0, first))).isFalse();
        // another sound
        assertThat(SonifyEngine.alreadyPlaying(playing, new ClipVoice(other.samples(), 0, new Playing(other)))).isFalse();
        // a built-in sound
        assertThat(SonifyEngine.alreadyPlaying(playing, new ClipVoice(new float[10], 0, null))).isFalse();
    }

    /** A WAV file of silence, this many seconds long. */
    private Path wav(int seconds) throws Exception {
        int frames = 8000 * seconds;
        AudioFormat format = new AudioFormat(8000, 16, 1, true, false);
        Path file = directory.resolve(seconds + "s.wav");
        try (AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(new byte[frames * 2]), format,
                frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }
}
