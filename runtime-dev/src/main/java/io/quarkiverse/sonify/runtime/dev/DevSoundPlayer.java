package io.quarkiverse.sonify.runtime.dev;

import jakarta.inject.Singleton;

import io.quarkiverse.sonify.SoundPlayer;

/**
 * The {@link SoundPlayer} of an application that makes sounds: in dev mode, or in production or tests that opt in.
 */
@Singleton
public class DevSoundPlayer implements SoundPlayer {

    @Override
    public void play(String name) {
        SonifyEngine.get().custom(name);
    }
}
