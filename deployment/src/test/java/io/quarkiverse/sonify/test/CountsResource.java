package io.quarkiverse.sonify.test;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkiverse.sonify.runtime.dev.SonifyEngine;

/**
 * Reads the counters of the engine from inside the application, where the engine is the one that the extension uses.
 * It lives under {@code /q/}, which Sonify does not listen to, so that reading the counters does not change them.
 */
@Path("/q/sonify-test")
public class CountsResource {

    @GET
    @Path("requests")
    public long requests() {
        return SonifyEngine.get().requestCount();
    }

    @GET
    @Path("responses")
    public long responses() {
        return SonifyEngine.get().responseCount();
    }

    @GET
    @Path("customs")
    public long customs() {
        return SonifyEngine.get().customCount();
    }
}
