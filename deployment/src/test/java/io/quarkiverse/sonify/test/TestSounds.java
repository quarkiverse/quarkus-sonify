package io.quarkiverse.sonify.test;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.SoundRegistry;
import io.quarkiverse.sonify.Wave;

@ApplicationScoped
public class TestSounds implements SonifyCustomizer {

    static final AtomicBoolean CALLED = new AtomicBoolean();

    @Override
    public void customize(SoundRegistry sounds) {
        CALLED.set(true);
        sounds.sound("order-placed").tone(Wave.SINE, 440, 0, 100);
    }
}
