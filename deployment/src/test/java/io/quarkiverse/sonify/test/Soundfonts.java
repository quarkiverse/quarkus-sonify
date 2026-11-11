package io.quarkiverse.sonify.test;

/**
 * Whether a soundfont can be used in dev mode and in tests: Quarkus exports the package of the JDK synthesizer that it
 * needs from Java 25 on. On an older Java, Sonify falls back to the default instruments.
 */
final class Soundfonts {

    private Soundfonts() {
    }

    static String expected() {
        return String.valueOf(Runtime.version().feature() >= 25);
    }
}
