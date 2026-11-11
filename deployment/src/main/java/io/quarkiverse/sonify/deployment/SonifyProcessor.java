package io.quarkiverse.sonify.deployment;

import org.jboss.logging.Logger;

import io.quarkiverse.sonify.runtime.NoopSoundPlayer;
import io.quarkiverse.sonify.runtime.SonifyBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.runtime.LaunchMode;

/**
 * The build steps of every launch mode. Nothing here makes a sound: see {@link SonifyDevProcessor}.
 */
class SonifyProcessor {

    private static final Logger LOG = Logger.getLogger(SonifyProcessor.class);

    private static final String FEATURE = "sonify";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Where Sonify is not active, an injected {@code SoundPlayer} does nothing, and {@code @PlaySound} has no interceptor.
     */
    @BuildStep(onlyIfNot = SonifyActive.class)
    AdditionalBeanBuildItem silentSoundPlayer() {
        return new AdditionalBeanBuildItem(NoopSoundPlayer.class);
    }

    /**
     * Sounds outside of dev mode need two things: when only one of them is there, it does nothing, and that is worth a
     * word. A dependency without {@code quarkus.sonify.test.enabled} is fine in tests: the dependency is there for the
     * production application.
     */
    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void checkOptIn(LaunchModeBuildItem launchMode, SonifyBuildTimeConfig config) {
        LaunchMode mode = launchMode.getLaunchMode();
        boolean present = SonifyActive.devArtifactPresent();
        if (mode == LaunchMode.NORMAL || mode == LaunchMode.RUN) {
            boolean enabled = config.production().enabled();
            if (enabled && !present) {
                LOG.warn(MISSING_DEPENDENCY.formatted("quarkus.sonify.production.enabled", "the production application"));
            } else if (!enabled && present) {
                LOG.warn("The application depends on io.quarkiverse.sonify:quarkus-sonify-dev, but "
                        + "quarkus.sonify.production.enabled is false: the production application makes no sound. "
                        + "Remove the dependency, or set the property to true");
            }
        } else if (mode == LaunchMode.TEST && config.test().enabled() && !present) {
            LOG.warn(MISSING_DEPENDENCY.formatted("quarkus.sonify.test.enabled", "the tests"));
        }
    }

    private static final String MISSING_DEPENDENCY = "%s is true, but the application does not depend on "
            + "io.quarkiverse.sonify:quarkus-sonify-dev: there are no sounds in %s";
}
