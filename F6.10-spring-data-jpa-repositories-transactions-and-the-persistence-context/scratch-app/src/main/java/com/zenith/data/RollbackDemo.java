package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 21: the same two-write method, once without and once with an unchecked exception halfway.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=rollback
@Component
@Profile("rollback")
class RollbackDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final OrderService service;
    private final OrderRepository orders;

    RollbackDemo(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. placeTwoOrders without a failure");
        service.placeTwoOrders(SUFI_NIGHT, false);
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. placeTwoOrders with a RuntimeException halfway");
        try {
            service.placeTwoOrders(SUFI_NIGHT, true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 3. one more order, to see which id comes next");
        long id = service.placeOrder(SUFI_NIGHT, "meera@example.com", 2);
        System.out.println(">>>    placeOrder returned id " + id);
    }
}
