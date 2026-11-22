package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 25: the trap and the fix, side by side. Neither createOrder has an annotation, and both
// lead to a @Transactional persistOrder that fails after the save.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=fix
@Component
@Profile("fix")
class FixDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final CheckoutService checkout;
    private final FixedCheckoutService fixed;
    private final OrderRepository orders;

    FixDemo(CheckoutService checkout, FixedCheckoutService fixed, OrderRepository orders) {
        this.checkout = checkout;
        this.fixed = fixed;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. the trap: checkout.createOrder(...), which calls this.persistOrder(...)");
        try {
            checkout.createOrder(SUFI_NIGHT, "trap@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. the fix: fixed.createOrder(...), which calls writer.persistOrder(...)");
        try {
            fixed.createOrder(SUFI_NIGHT, "fixed@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());
    }
}
