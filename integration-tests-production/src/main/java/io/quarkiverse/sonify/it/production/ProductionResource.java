package io.quarkiverse.sonify.it.production;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.quarkiverse.sonify.PlaySound;
import io.quarkiverse.sonify.SoundPlayer;
import io.quarkiverse.sonify.runtime.dev.SonifyEngine;

@Path("/production")
@ApplicationScoped
public class ProductionResource {

    @Inject
    SoundPlayer soundPlayer;

    @Inject
    ProductionSounds sounds;

    @POST
    @Path("orders")
    @PlaySound("order-placed")
    public String order() {
        return "ordered";
    }

    @GET
    @Path("audit")
    public String audit() {
        soundPlayer.play("audit");
        return "audited";
    }

    @GET
    @Path("player")
    public String player() {
        return soundPlayer.getClass().getName();
    }

    @GET
    @Path("customized")
    public boolean customized() {
        return sounds.called();
    }

    /** The sound file of the configuration is in the application, native executable included. */
    @GET
    @Path("sound-file")
    public boolean soundFile() {
        return Thread.currentThread().getContextClassLoader().getResource("sounds/coin.wav") != null;
    }

    /** The counters of the engine. Reading one is a request too, and it is counted. */
    @GET
    @Path("counts/requests")
    public long requests() {
        return SonifyEngine.get().requestCount();
    }

    @GET
    @Path("counts/customs")
    public long customs() {
        return SonifyEngine.get().customCount();
    }
}
