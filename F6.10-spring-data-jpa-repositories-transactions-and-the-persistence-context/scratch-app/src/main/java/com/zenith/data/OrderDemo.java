package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 20: save one ticket order, and see the id the database picks.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=order
@Component
@Profile("order")
class OrderDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final OrderService service;
    private final OrderRepository orders;

    OrderDemo(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. orders in the table: " + orders.count());

        System.out.println(">>> 2. calling placeOrder");
        long id = service.placeOrder(SUFI_NIGHT, "meera@example.com", 2);
        System.out.println(">>>    placeOrder returned id " + id);

        System.out.println(">>> 3. orders in the table: " + orders.count());
    }
}
