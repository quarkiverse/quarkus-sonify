package io.quarkiverse.sonify.runtime;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Where Sonify makes sounds besides dev mode. Both need the application to depend on
 * {@code io.quarkiverse.sonify:quarkus-sonify-dev} as well, which holds the code that makes sounds: the property and
 * the dependency together turn sounds on, and either one alone does nothing.
 */
@ConfigMapping(prefix = "quarkus.sonify")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface SonifyBuildTimeConfig {

    /**
     * Sounds in a production application.
     */
    Production production();

    /**
     * Sounds in the tests of the application.
     */
    Test test();

    interface Production {

        /**
         * Whether a production application makes sounds, like in dev mode. This is for applications that run on a
         * machine with someone in front of it, such as a desktop application or a kiosk.
         * <p>
         * The application must also depend on {@code io.quarkiverse.sonify:quarkus-sonify-dev}.
         */
        @WithDefault("false")
        boolean enabled();
    }

    interface Test {

        /**
         * Whether the application makes sounds while its tests run.
         * <p>
         * The application must also depend on {@code io.quarkiverse.sonify:quarkus-sonify-dev}.
         */
        @WithDefault("false")
        boolean enabled();
    }
}
