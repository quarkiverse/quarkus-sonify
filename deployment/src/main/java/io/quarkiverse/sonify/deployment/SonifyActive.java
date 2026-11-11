package io.quarkiverse.sonify.deployment;

import java.util.function.BooleanSupplier;

import io.quarkiverse.sonify.runtime.SonifyBuildTimeConfig;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.dev.spi.DevModeType;
import io.quarkus.runtime.LaunchMode;

/**
 * Whether the application makes sounds: always in local dev mode. A production application, or the tests of an
 * application, only make sounds when the application asks for it twice: with {@code quarkus.sonify.production.enabled}
 * or {@code quarkus.sonify.test.enabled}, and with a dependency on {@code quarkus-sonify-dev}.
 */
class SonifyActive implements BooleanSupplier {

    /** A class of {@code quarkus-sonify-dev}, which an application only has outside of dev mode when it depends on it. */
    static final String DEV_ARTIFACT_CLASS = "io.quarkiverse.sonify.runtime.dev.SonifyEngine";

    private final boolean active;

    SonifyActive(LaunchMode launchMode, DevModeType devModeType, SonifyBuildTimeConfig config) {
        this.active = switch (launchMode) {
            case DEVELOPMENT -> devModeType != DevModeType.REMOTE_SERVER_SIDE;
            // RUN is a production application started by the build tool (quarkus:run)
            case NORMAL, RUN -> config.production().enabled() && devArtifactPresent();
            case TEST -> config.test().enabled() && devArtifactPresent();
        };
    }

    static boolean devArtifactPresent() {
        return QuarkusClassLoader.isClassPresentAtRuntime(DEV_ARTIFACT_CLASS);
    }

    @Override
    public boolean getAsBoolean() {
        return active;
    }
}
