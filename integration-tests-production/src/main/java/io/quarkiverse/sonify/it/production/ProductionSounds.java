package io.quarkiverse.sonify.it.production;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.SoundRegistry;
import io.quarkiverse.sonify.Wave;

@ApplicationScoped
public class ProductionSounds implements SonifyCustomizer {

    private volatile boolean called;

    @Override
    public void customize(SoundRegistry sounds) {
        called = true;
        sounds.sound("audit").tone(Wave.SINE, 440, 0, 100);
    }

    public boolean called() {
        return called;
    }
}
