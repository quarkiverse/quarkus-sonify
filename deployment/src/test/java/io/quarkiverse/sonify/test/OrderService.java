package io.quarkiverse.sonify.test;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.sonify.PlaySound;

@ApplicationScoped
public class OrderService {

    private final AtomicInteger orders = new AtomicInteger();

    @PlaySound("order-placed")
    public int place() {
        return orders.incrementAndGet();
    }
}
