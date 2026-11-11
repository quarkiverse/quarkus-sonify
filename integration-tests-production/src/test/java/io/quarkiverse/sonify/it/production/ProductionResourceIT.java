package io.quarkiverse.sonify.it.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * The packaged application, as a jar or as a native executable, makes sounds.
 */
@QuarkusIntegrationTest
public class ProductionResourceIT extends ProductionResourceTest {

    @Test
    public void theDevArtifactIsPackaged() throws IOException {
        Path libraries = Path.of("target", "quarkus-app", "lib", "main");
        if (!Files.isDirectory(libraries)) {
            // a native build has no directory of libraries
            return;
        }
        try (Stream<Path> files = Files.list(libraries)) {
            assertThat(files.map(file -> file.getFileName().toString()))
                    .anyMatch(name -> name.contains("quarkus-sonify-dev"));
        }
    }
}
