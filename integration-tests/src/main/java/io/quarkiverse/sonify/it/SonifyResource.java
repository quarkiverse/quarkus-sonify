package io.quarkiverse.sonify.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import io.quarkiverse.sonify.PlaySound;
import io.quarkiverse.sonify.SoundPlayer;

@Path("/sonify")
@ApplicationScoped
public class SonifyResource {

    @Inject
    SoundPlayer soundPlayer;

    @Inject
    ItSounds sounds;

    /** The whole API in one request: the annotation, the injected player and a customizer. */
    @GET
    @PlaySound("hello")
    public String hello() {
        soundPlayer.play("audit");
        return "Hello sonify";
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

    @GET
    @Path("dev-classes")
    public boolean devClasses() {
        try {
            Class.forName("io.quarkiverse.sonify.runtime.dev.SonifyEngine");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
