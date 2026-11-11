package io.quarkiverse.sonify;

/**
 * Defines sounds in code. Every CDI bean that implements this interface is called when the application starts in dev
 * mode, and again on every live reload:
 *
 * <pre>
 * &#64;ApplicationScoped
 * public class MySounds implements SonifyCustomizer {
 *
 *     &#64;Override
 *     public void customize(SoundRegistry sounds) {
 *         sounds.sound("shipped").tone(Wave.SINE, 392, 0, 150).tone(Wave.SINE, 523, 120, 300);
 *         sounds.status(404).off();
 *     }
 * }
 * </pre>
 *
 * A {@code quarkus.sonify.*} property for the same sound wins over what the customizer says, so that a sound can be
 * changed or silenced without touching the code.
 * <p>
 * Where Sonify makes no sound (outside of dev mode, unless the application opts in), nothing calls the customizer, and
 * the bean is removed from the application as unused.
 */
public interface SonifyCustomizer {

    void customize(SoundRegistry sounds);
}
