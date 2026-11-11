package io.quarkiverse.sonify.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

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
 * A production application that depends on {@code quarkus-sonify-dev}, as every application does in the tests of the
 * extension, but does not set {@code quarkus.sonify.production.enabled}: it is silent, and the build says why.
 */
public class SonifyProductionWithoutOptInTest {

    @RegisterExtension
    static final QuarkusProdModeTest production = new QuarkusProdModeTest()
            .withApplicationRoot(jar -> jar.addClasses(SoundsResource.class, OrderService.class, TestSounds.class))
            .setApplicationName("sonify-production-without-opt-in")
            .setApplicationVersion("1.0")
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-rest-deployment", Version.getVersion())))
            .setLogRecordPredicate(record -> record.getLoggerName().startsWith("io.quarkiverse.sonify"))
            .setRun(true);

    @ProdBuildResults
    ProdModeTestResults results;

    @Test
    public void theApplicationIsSilentAndTheBuildSaysWhy() {
        assertThat(results.getRetainedBuildLogRecords())
                .extracting(record -> record.getMessage())
                .singleElement()
                .asString()
                .contains("quarkus.sonify.production.enabled is false");
        RestAssured.get("/player").then().statusCode(200).body(is("io.quarkiverse.sonify.runtime.NoopSoundPlayer"));
        RestAssured.get("/customized").then().statusCode(200).body(is("false"));
        RestAssured.post("/orders").then().statusCode(200).body(is("order 1"));
    }
}
