package io.quarkiverse.sonify.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.time.Duration;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;

/**
 * Dev mode is where Sonify does something. Sounds are disabled, so that the test is silent and does not need an audio
 * output: every event still reaches the engine, which counts it.
 */
public class SonifyDevModeTest {

    @RegisterExtension
    static final QuarkusDevModeTest devMode = new QuarkusDevModeTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(SoundsResource.class, OrderService.class, TestSounds.class, CountsResource.class)
                    .addAsResource(new StringAsset("quarkus.sonify.enabled=false\n"), "application.properties"));

    @Test
    public void requestsAndResponsesAreHeard() {
        long requests = count("requests");
        long responses = count("responses");

        RestAssured.get("/hello").then().statusCode(200).body(is("hello"));
        RestAssured.get("/no-such-path").then().statusCode(404);

        assertThat(count("requests") - requests).isEqualTo(2);
        // the sound of a response is asked for when the response has been written, which the client may see first
        await().atMost(Duration.ofSeconds(5)).until(() -> count("responses") - responses == 2);
    }

    @Test
    public void customSoundsAreHeard() {
        RestAssured.get("/player").then().statusCode(200)
                .body(is("io.quarkiverse.sonify.runtime.dev.DevSoundPlayer"));
        RestAssured.get("/customized").then().statusCode(200).body(is("true"));
        long customs = count("customs");

        // a bean method annotated with @PlaySound
        RestAssured.post("/orders").then().statusCode(200).body(is("order 1"));
        assertThat(count("customs") - customs).isEqualTo(1);

        // the injected SoundPlayer
        RestAssured.get("/audit").then().statusCode(200).body(is("audited"));
        assertThat(count("customs") - customs).isEqualTo(2);
    }

    @Test
    public void soundsSurviveALiveReload() {
        RestAssured.get("/hello").then().statusCode(200).body(is("hello"));

        devMode.modifySourceFile(SoundsResource.class, source -> source.replace("return \"hello\"", "return \"bonjour\""));

        long requests = count("requests");
        long customs = count("customs");
        RestAssured.get("/hello").then().statusCode(200).body(is("bonjour"));
        RestAssured.get("/audit").then().statusCode(200).body(is("audited"));
        // the engine is not part of the application, so its counters are not reset by the reload
        assertThat(count("requests") - requests).isEqualTo(2);
        assertThat(count("customs") - customs).isEqualTo(1);
    }

    private static long count(String counter) {
        return Long.parseLong(RestAssured.get("/q/sonify-test/" + counter).then().statusCode(200).extract().asString());
    }
}
