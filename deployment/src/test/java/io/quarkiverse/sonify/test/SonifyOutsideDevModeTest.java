package io.quarkiverse.sonify.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.sonify.SoundPlayer;
import io.quarkiverse.sonify.runtime.NoopSoundPlayer;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine;
import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Outside of dev mode (here: in a test, the same goes for production), the API is there and does nothing.
 */
public class SonifyOutsideDevModeTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SoundsResource.class, OrderService.class, TestSounds.class));

    @Inject
    SoundPlayer soundPlayer;

    @Test
    public void theApiDoesNothing() {
        long requests = SonifyEngine.get().requestCount();
        long customs = SonifyEngine.get().customCount();

        assertThat(soundPlayer).isInstanceOf(NoopSoundPlayer.class);
        RestAssured.get("/hello").then().statusCode(200).body(is("hello"));
        RestAssured.post("/orders").then().statusCode(200).body(is("order 1"));
        RestAssured.get("/audit").then().statusCode(200).body(is("audited"));

        // nothing listens to the requests, and nothing reaches the engine
        assertThat(SonifyEngine.get().requestCount()).isEqualTo(requests);
        assertThat(SonifyEngine.get().customCount()).isEqualTo(customs);
        // the customizers of the application are not called
        RestAssured.get("/customized").then().statusCode(200).body(is("false"));
    }
}
