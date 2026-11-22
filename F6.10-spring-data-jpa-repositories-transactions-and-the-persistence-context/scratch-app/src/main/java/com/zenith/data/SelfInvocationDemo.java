package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 24: the same failing persistOrder, called two ways: straight from outside, and through
// createOrder, which calls this.persistOrder.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=self-invocation
@Component
@Profile("self-invocation")
class SelfInvocationDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final CheckoutService checkout;
    private final OrderRepository orders;

    SelfInvocationDemo(CheckoutService checkout, OrderRepository orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. checkout.persistOrder(...) from outside, failing after the save");
        try {
            checkout.persistOrder(SUFI_NIGHT, "first@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. checkout.createOrder(...), which calls this.persistOrder(...)");
        try {
            checkout.createOrder(SUFI_NIGHT, "second@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());
    }
}
