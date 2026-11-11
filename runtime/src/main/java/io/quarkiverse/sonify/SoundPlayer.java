package io.quarkiverse.sonify;

/**
 * Plays a sound from anywhere in the application: inject it and call {@link #play(String)}.
 * <p>
 * In dev mode the sound comes out of the machine that runs the application. In a production application and in tests
 * the injected bean does nothing, so calls can stay in the code, unless the application opts in to sounds there.
 */
public interface SoundPlayer {

    /**
     * Plays the sound with this name and returns immediately.
     * <p>
     * {@code quarkus.sonify.sound."<name>"} can point to a WAV file or be {@code off}. A name without configuration
     * gets a short sound of its own, derived from the name.
     *
     * @param name the name of the sound
     */
    void play(String name);
}
