package io.quarkiverse.sonify.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Sonify only makes sounds in dev mode. Everywhere else the application works the same, in silence.
 */
@QuarkusTest
public class SonifyResourceTest {

    @Test
    public void theApiDoesNothing() {
        given().when().get("/sonify").then().statusCode(200).body(is("Hello sonify"));
        given().when().get("/sonify/player").then().statusCode(200)
                .body(is("io.quarkiverse.sonify.runtime.NoopSoundPlayer"));
        given().when().get("/sonify/customized").then().statusCode(200).body(is("false"));
    }
}
