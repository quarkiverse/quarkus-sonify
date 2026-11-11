package io.quarkiverse.sonify.deployment;

import io.quarkiverse.sonify.PlaySound;
import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.runtime.SonifyConfig;
import io.quarkiverse.sonify.runtime.dev.DevSoundPlayer;
import io.quarkiverse.sonify.runtime.dev.PlaySoundInterceptor;
import io.quarkiverse.sonify.runtime.dev.SonifyRequestHandler;
import io.quarkiverse.sonify.runtime.dev.SonifyStartup;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.core.deployment.service.ServiceRegistrar;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.ModuleExportBuildItem;
import io.quarkus.deployment.builditem.nativeimage.JniRuntimeAccessBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.vertx.http.deployment.FilterBuildItem;

/**
 * Everything that makes a sound. These build steps run in local dev mode, and in a production application or tests that
 * opt in (see {@link SonifyActive}). The classes they wire come from the {@code quarkus-sonify-dev} artifact, which is a
 * conditional dev dependency: outside of dev mode, an application only has it when it depends on it.
 */
@BuildSteps(onlyIf = SonifyActive.class)
class SonifyDevProcessor {

    /** Runs before authentication and CORS, so that rejected requests are heard too. */
    private static final int FILTER_PRIORITY = 1000;

    /**
     * Configures the engine and plays the chime, on every start of the application. The service needs the container,
     * because it looks up the customizers of the application.
     */
    @BuildStep
    void startup(ServiceRegistrar serviceRegistrar) {
        serviceRegistrar
                .forService("io.quarkiverse.sonify.startup")
                .require(SonifyConfig.class)
                .require(ArcContainer.class)
                .onStart((ctx, config, container) -> SonifyStartup.started(config, container));
    }

    @BuildStep
    FilterBuildItem requests() {
        return new FilterBuildItem(new SonifyRequestHandler(), FILTER_PRIORITY);
    }

    /** Nothing injects a customizer, so it would be removed as unused. Where Sonify is not active, that is what happens. */
    @BuildStep
    UnremovableBeanBuildItem customizers() {
        return UnremovableBeanBuildItem.beanTypes(SonifyCustomizer.class);
    }

    @BuildStep
    AdditionalBeanBuildItem customSounds() {
        return new AdditionalBeanBuildItem(PlaySound.class, PlaySoundInterceptor.class, DevSoundPlayer.class);
    }

    /**
     * What the audio code of the JDK needs in a native executable: its native library calls back into these classes, and
     * it finds the audio devices and the sound file readers through the service loader. Outside of a native build, these
     * build items are not used.
     */
    /**
     * MIDI sounds are rendered with the soundfont of the configuration through the {@code AudioSynthesizer} interface of
     * the JDK, which its module does not export. Without it, MIDI is played with the default instruments of the JDK.
     * The module name of the extension does not exist yet: Quarkus exports the package to the unnamed module of the
     * application instead.
     */
    @BuildStep
    ModuleExportBuildItem midiRendering() {
        return new ModuleExportBuildItem("java.desktop", "io.quarkiverse.sonify.runtime.dev", "com.sun.media.sound");
    }

    /** The rendering of MIDI calls the synthesizer of the JDK through reflection. */
    @BuildStep
    ReflectiveClassBuildItem nativeMidiRendering() {
        return ReflectiveClassBuildItem.builder("com.sun.media.sound.AudioSynthesizer", "com.sun.media.sound.SoftSynthesizer")
                .methods()
                .reason(getClass().getName())
                .build();
    }

    @BuildStep
    JniRuntimeAccessBuildItem nativeAudioCallbacks() {
        return new JniRuntimeAccessBuildItem(true, true, false,
                "com.sun.media.sound.DirectAudioDevice",
                "com.sun.media.sound.DirectAudioDeviceProvider$DirectAudioDeviceInfo",
                "com.sun.media.sound.PortMixerProvider$PortMixerInfo",
                "java.lang.Boolean");
    }

    /**
     * The audio classes of the JDK load their native library when they are initialized: that must happen in the
     * executable, not during the build.
     */
    @BuildStep
    RuntimeInitializedPackageBuildItem nativeAudioAtRunTime() {
        return new RuntimeInitializedPackageBuildItem("com.sun.media.sound");
    }

    /**
     * The sound files and the soundfont of the configuration are resources of the application, which a native executable
     * only contains when they are listed. The configuration is read at run time and may name any file, so all the WAV
     * and MIDI files and all the soundfonts of the application are included.
     */
    @BuildStep
    NativeImageResourcePatternsBuildItem nativeSoundFiles() {
        return NativeImageResourcePatternsBuildItem.builder()
                .includeGlobs("**/*.wav", "**/*.mid", "**/*.midi", "**/*.sf2", "**/*.dls")
                .build();
    }

    /**
     * Quarkus does not let the native image find service providers by itself, so the providers of the JDK that play
     * sounds, read sound files, convert their format, and play MIDI are listed here.
     */
    @BuildStep
    void nativeAudioProviders(BuildProducer<ServiceProviderBuildItem> providers) {
        providers.produce(new ServiceProviderBuildItem("javax.sound.sampled.spi.MixerProvider",
                "com.sun.media.sound.DirectAudioDeviceProvider",
                "com.sun.media.sound.PortMixerProvider"));
        providers.produce(new ServiceProviderBuildItem("javax.sound.sampled.spi.AudioFileReader",
                "com.sun.media.sound.AiffFileReader",
                "com.sun.media.sound.AuFileReader",
                "com.sun.media.sound.WaveFileReader",
                "com.sun.media.sound.WaveFloatFileReader",
                "com.sun.media.sound.WaveExtensibleFileReader"));
        providers.produce(new ServiceProviderBuildItem("javax.sound.sampled.spi.FormatConversionProvider",
                "com.sun.media.sound.AlawCodec",
                "com.sun.media.sound.AudioFloatFormatConverter",
                "com.sun.media.sound.PCMtoPCMCodec",
                "com.sun.media.sound.UlawCodec"));
        // MIDI: the synthesizer, the readers of MIDI files and soundfonts, and MIDI rendered as audio
        providers.produce(new ServiceProviderBuildItem("javax.sound.midi.spi.MidiDeviceProvider",
                "com.sun.media.sound.SoftProvider"));
        providers.produce(new ServiceProviderBuildItem("javax.sound.midi.spi.MidiFileReader",
                "com.sun.media.sound.StandardMidiFileReader"));
        providers.produce(new ServiceProviderBuildItem("javax.sound.midi.spi.MidiFileWriter",
                "com.sun.media.sound.StandardMidiFileWriter"));
        providers.produce(new ServiceProviderBuildItem("javax.sound.midi.spi.SoundbankReader",
                "com.sun.media.sound.SF2SoundbankReader",
                "com.sun.media.sound.DLSSoundbankReader"));
        providers.produce(new ServiceProviderBuildItem("javax.sound.sampled.spi.AudioFileReader",
                "com.sun.media.sound.SoftMidiAudioFileReader"));
    }
}
