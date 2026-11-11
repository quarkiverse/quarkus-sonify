package io.quarkiverse.sonify.runtime.dev;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.quarkiverse.sonify.SonifyTheme;
import io.quarkiverse.sonify.runtime.SonifyConfig;

/**
 * A configuration for tests. Sounds are disabled, so that a test never opens an audio output: everything but the
 * actual playing still happens.
 */
final class TestConfig implements SonifyConfig {

    double volume = 0.4;
    String soundfont;
    final Map<String, String> method = new HashMap<>();
    final Map<String, String> status = new HashMap<>();
    final Map<String, String> sound = new HashMap<>();

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public double volume() {
        return volume;
    }

    @Override
    public Duration responseGap() {
        return Duration.ofMillis(350);
    }

    @Override
    public SonifyTheme theme() {
        return SonifyTheme.TAPS;
    }

    @Override
    public Map<String, String> method() {
        return method;
    }

    @Override
    public Map<String, String> status() {
        return status;
    }

    @Override
    public Map<String, String> sound() {
        return sound;
    }

    @Override
    public Optional<String> soundfont() {
        return Optional.ofNullable(soundfont);
    }
}
