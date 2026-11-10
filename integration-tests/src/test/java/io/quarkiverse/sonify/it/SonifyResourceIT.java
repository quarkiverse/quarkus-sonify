package io.quarkiverse.sonify.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * The packaged application: the code that makes sounds is not part of it.
 */
@QuarkusIntegrationTest
public class SonifyResourceIT extends SonifyResourceTest {

    @Test
    public void theDevClassesAreNotInTheApplication() {
        given().when().get("/sonify/dev-classes").then().statusCode(200).body(is("false"));
    }

    @Test
    public void theDevArtifactIsNotPackaged() throws IOException {
        Path libraries = Path.of("target", "quarkus-app", "lib", "main");
        if (!Files.isDirectory(libraries)) {
            // a native build has no directory of libraries
            return;
        }
        try (Stream<Path> files = Files.list(libraries)) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .anyMatch(name -> name.contains("quarkus-sonify"))
                    .noneMatch(name -> name.contains("quarkus-sonify-dev"));
        }
    }
}
