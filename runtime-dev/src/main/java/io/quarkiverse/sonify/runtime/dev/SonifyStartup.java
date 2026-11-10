package io.quarkiverse.sonify.runtime.dev;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.sonify.SonifyCustomizer;
import io.quarkiverse.sonify.runtime.SonifyConfig;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

/**
 * What the startup service of Sonify does.
 */
public final class SonifyStartup {

    private static final Logger LOG = Logger.getLogger(SonifyStartup.class);

    private SonifyStartup() {
    }

    /**
     * Runs on every start of the application, so a change of the configuration is heard after a live reload.
     */
    public static void started(SonifyConfig config, ArcContainer container) {
        SonifyEngine engine = SonifyEngine.get();
        LOG.debugf("Sonify so far: %d requests, %d responses, %d custom sounds", engine.requestCount(),
                engine.responseCount(), engine.customCount());
        List<SonifyCustomizer> customizers = new ArrayList<>();
        for (InstanceHandle<SonifyCustomizer> handle : container.listAll(SonifyCustomizer.class)) {
            customizers.add(handle.get());
        }
        // sound files are resources of the application: its class loader is the context class loader here
        engine.configure(config, Thread.currentThread().getContextClassLoader(), customizers);
        engine.chime();
    }
}
