package io.quarkiverse.sonify.runtime;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.sonify.SonifyTheme;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * These properties are read when the application starts, and in dev mode again on every live reload. Where Sonify
 * makes no sound (outside of dev mode, unless the application opts in), they are ignored.
 */
@ConfigMapping(prefix = "quarkus.sonify")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface SonifyConfig {

    /**
     * Whether sounds are played. Set it to {@code false} to silence everything without removing the extension.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * The overall volume, from {@code 0} (silent) to {@code 1} (loudest).
     */
    @WithDefault("0.4")
    double volume();

    /**
     * The shortest time between the start of the sound of a request and the start of the sound of its response, so
     * that the two are heard apart even when the response is immediate. A request that takes longer than this is not
     * delayed.
     */
    @WithDefault("350ms")
    Duration responseGap();

    /**
     * How long a sound may last: sound files, computed audio, tones, MIDI and notes are cut after it. Up to 10 seconds.
     * <p>
     * A sound that is still playing is not started again, so a long sound does not pile up when many requests ask for it.
     */
    @WithDefault("2s")
    Duration maxSoundLength();

    /**
     * The built-in set of sounds to use for everything that is not overridden.
     */
    @WithDefault("taps")
    SonifyTheme theme();

    /**
     * The sound of a request with this HTTP method, instead of the sound of the theme: the path of a WAV or MIDI file,
     * notes after {@code notes:} (such as {@code notes:C5/8 E5/8 G5/4}), or {@code off}.
     * <p>
     * A file is looked up in the resources of the application first, and then on the file system. At most its first two
     * seconds are played.
     */
    @ConfigDocMapKey("http-method")
    Map<String, String> method();

    /**
     * The sound of a response with this status, instead of the sound of the theme: the path of a WAV or MIDI file, notes
     * after {@code notes:}, or {@code off}.
     * <p>
     * The key is either a status code ({@code "404"}) or a whole class of them ({@code "4xx"}). A status code wins
     * over its class.
     */
    @ConfigDocMapKey("status")
    Map<String, String> status();

    /**
     * The sound with this name, as used by {@code @PlaySound} and {@code SoundPlayer}: the path of a WAV or MIDI file,
     * notes after {@code notes:}, or {@code off}. A name that is not configured gets a short sound of its own, derived from the
     * name.
     */
    @ConfigDocMapKey("sound-name")
    Map<String, String> sound();

    /**
     * A soundfont, whose instruments play the MIDI sounds: MIDI files, and sequences defined in code. A SoundFont 2
     * ({@code .sf2}) or DLS ({@code .dls}) file, looked up in the resources of the application first, and then on the
     * file system. Without it, MIDI is played with the default instruments of the JDK.
     */
    Optional<String> soundfont();
}
