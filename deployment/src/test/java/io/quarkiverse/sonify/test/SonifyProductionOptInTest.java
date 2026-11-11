package io.quarkiverse.sonify.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;
import io.restassured.RestAssured;

/**
 * A production application makes sounds with {@code quarkus.sonify.production.enabled}: the other half of the opt-in,
 * the dependency on {@code quarkus-sonify-dev}, is always there in the tests of the extension.
 */
public class SonifyProductionOptInTest {

    @RegisterExtension
    static final QuarkusProdModeTest production = new QuarkusProdModeTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(SoundsResource.class, OrderService.class, TestSounds.class, CountsResource.class))
            .setApplicationName("sonify-production")
            .setApplicationVersion("1.0")
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-rest-deployment", Version.getVersion())))
            .overrideConfigKey("quarkus.sonify.production.enabled", "true")
            // silent: every event still reaches the engine, which counts it
            .overrideConfigKey("quarkus.sonify.enabled", "false")
            .setLogRecordPredicate(record -> record.getLoggerName().startsWith("io.quarkiverse.sonify"))
            .setRun(true);

    @ProdBuildResults
    ProdModeTestResults results;

    @Test
    public void soundsArePlayedInProduction() {
        assertThat(results.getRetainedBuildLogRecords()).isEmpty();
        RestAssured.get("/player").then().statusCode(200)
                .body(is("io.quarkiverse.sonify.runtime.dev.DevSoundPlayer"));
        RestAssured.get("/customized").then().statusCode(200).body(is("true"));

        long requests = Counts.of("requests");
        long responses = Counts.of("responses");
        long customs = Counts.of("customs");

        RestAssured.post("/orders").then().statusCode(200);

        assertThat(Counts.of("requests") - requests).isEqualTo(1);
        assertThat(Counts.of("customs") - customs).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).until(() -> Counts.of("responses") - responses == 1);
    }
}
