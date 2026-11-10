package io.quarkiverse.sonify.test;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.quarkiverse.sonify.SoundPlayer;

@Path("/")
public class SoundsResource {

    @Inject
    SoundPlayer soundPlayer;

    @Inject
    OrderService orderService;

    @GET
    @Path("hello")
    public String hello() {
        return "hello";
    }

    @POST
    @Path("orders")
    public String order() {
        return "order " + orderService.place();
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
        return TestSounds.CALLED.get();
    }
}
