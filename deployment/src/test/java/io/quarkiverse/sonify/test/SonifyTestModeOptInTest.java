package io.quarkiverse.sonify.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.time.Duration;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.restassured.RestAssured;

/**
 * Tests make sounds with {@code quarkus.sonify.test.enabled}: the other half of the opt-in, the dependency on
 * {@code quarkus-sonify-dev}, is always there in the tests of the extension.
 */
public class SonifyTestModeOptInTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SoundsResource.class, OrderService.class, TestSounds.class, CountsResource.class,
                            Counts.class, Soundfonts.class)
                    .addAsResource("square.sf2"))
            .overrideConfigKey("quarkus.sonify.test.enabled", "true")
            .overrideConfigKey("quarkus.sonify.soundfont", "square.sf2")
            // silent: every event still reaches the engine, which counts it
            .overrideConfigKey("quarkus.sonify.enabled", "false");

    @Test
    public void soundsArePlayedInTests() {
        RestAssured.get("/player").then().statusCode(200)
                .body(is("io.quarkiverse.sonify.runtime.dev.DevSoundPlayer"));
        RestAssured.get("/customized").then().statusCode(200).body(is("true"));
        // Quarkus exports the package of the JDK synthesizer to the application in tests too, from Java 25 on
        RestAssured.get("/q/sonify-test/soundfont").then().statusCode(200).body(is(Soundfonts.expected()));

        long requests = Counts.of("requests");
        long responses = Counts.of("responses");
        long customs = Counts.of("customs");

        RestAssured.post("/orders").then().statusCode(200);

        assertThat(Counts.of("requests") - requests).isEqualTo(1);
        assertThat(Counts.of("customs") - customs).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> Counts.of("responses") - responses == 1);
    }
}
