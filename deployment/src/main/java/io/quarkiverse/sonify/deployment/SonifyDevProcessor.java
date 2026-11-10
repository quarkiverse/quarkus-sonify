package io.quarkiverse.sonify.deployment;

import io.quarkiverse.sonify.PlaySound;
import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.runtime.dev.DevSoundPlayer;
import io.quarkiverse.sonify.runtime.dev.PlaySoundInterceptor;
import io.quarkiverse.sonify.runtime.dev.SonifyRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.IsLocalDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.FilterBuildItem;

/**
 * Everything that makes a sound. These build steps only run in local dev mode, and the classes they wire come from the
 * {@code quarkus-sonify-dev} artifact, which is a conditional dev dependency: a production application has neither.
 */
@BuildSteps(onlyIf = IsLocalDevelopment.class)
class SonifyDevProcessor {

    /** Runs before authentication and CORS, so that rejected requests are heard too. */
    private static final int FILTER_PRIORITY = 1000;

    /**
     * @param beanContainer not used, but the recorder looks up the customizers of the application, so the container must
     *        be ready first
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    FilterBuildItem requests(SonifyRecorder recorder, BeanContainerBuildItem beanContainer) {
        recorder.started();
        return new FilterBuildItem(recorder.requestHandler(), FILTER_PRIORITY);
    }

    /** Nothing injects a customizer, so it would be removed as unused. Outside of dev mode, that is what happens. */
    @BuildStep
    UnremovableBeanBuildItem customizers() {
        return UnremovableBeanBuildItem.beanTypes(SonifyCustomizer.class);
    }

    @BuildStep
    AdditionalBeanBuildItem customSounds() {
        return new AdditionalBeanBuildItem(PlaySound.class, PlaySoundInterceptor.class, DevSoundPlayer.class);
    }
}
