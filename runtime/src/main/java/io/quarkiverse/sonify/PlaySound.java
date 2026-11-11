package io.quarkiverse.sonify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

/**
 * Plays a sound every time the annotated method of a CDI bean is called, or any business method of the annotated bean
 * class.
 * <p>
 * The sound is looked up by its name: {@code quarkus.sonify.sound."<name>"} can point to a WAV file or be {@code off}.
 * A name without configuration gets a short sound of its own, derived from the name.
 * <p>
 * This only does something in dev mode, unless the application opts in to sounds in production or in tests. Otherwise
 * there is no interceptor behind the annotation, so the method is not intercepted at all.
 */
@InterceptorBinding
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface PlaySound {

    /**
     * The name of the sound.
     */
    @Nonbinding
    String value();
}
