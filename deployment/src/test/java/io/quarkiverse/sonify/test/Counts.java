package io.quarkiverse.sonify.test;

import io.restassured.RestAssured;

/** Reads a counter of the engine of the running application, through {@link CountsResource}. */
final class Counts {

    private Counts() {
    }

    static long of(String counter) {
        return Long.parseLong(RestAssured.get("/q/sonify-test/" + counter).then().statusCode(200).extract().asString());
    }
}
