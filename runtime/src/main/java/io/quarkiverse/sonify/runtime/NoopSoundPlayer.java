package io.quarkiverse.sonify.runtime;

import jakarta.inject.Singleton;

import io.quarkiverse.sonify.SoundPlayer;

/**
 * The {@link SoundPlayer} of every application that makes no sound: outside of dev mode, unless it opts in. It does
 * nothing.
 */
@Singleton
public class NoopSoundPlayer implements SoundPlayer {

    @Override
    public void play(String name) {
    }
}
