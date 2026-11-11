package io.quarkiverse.sonify.it.production;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * An application that opts in makes sounds in its tests ({@code quarkus.sonify.test.enabled}), and the packaged
 * application in production ({@code quarkus.sonify.production.enabled}, see {@link ProductionResourceIT}).
 */
@QuarkusTest
public class ProductionResourceTest {

    @Test
    public void soundsArePlayed() {
        given().get("/production/player").then().statusCode(200)
                .body(is("io.quarkiverse.sonify.runtime.dev.DevSoundPlayer"));
        given().get("/production/customized").then().statusCode(200).body(is("true"));
        given().get("/production/sound-file").then().statusCode(200).body(is("true"));
        // a WAV file, a MIDI file, notes and a MIDI sequence from a customizer, the MIDI with the soundfont
        given().get("/production/custom-sounds").then().statusCode(200)
                .body(is("audit,from-code,from-file,from-notes,order-placed"));
        given().get("/production/soundfont").then().statusCode(200).body(is(String.valueOf(soundfontExpected())));

        long requests = counter("requests");
        long customs = counter("customs");

        // a custom sound from the annotation and one from the injected player
        given().post("/production/orders").then().statusCode(200).body(is("ordered"));
        given().get("/production/audit").then().statusCode(200).body(is("audited"));

        // the two requests, the read of the second counter, and this read itself
        assertThat(counter("requests") - requests).isEqualTo(4);
        assertThat(counter("customs") - customs).isEqualTo(2);
    }

    /**
     * In tests, Quarkus exports the package of the JDK synthesizer that the soundfont needs from Java 25 on. The packaged
     * application has it in the manifest of its jar, whatever the Java version.
     */
    protected boolean soundfontExpected() {
        return Runtime.version().feature() >= 25;
    }

    private static long counter(String name) {
        return Long.parseLong(given().get("/production/counts/" + name).then().statusCode(200).extract().asString());
    }
}
