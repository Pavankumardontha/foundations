package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 26: the second fix. createOrder has no annotation and calls self.persistOrder(...),
// which fails after the save.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=self-injection
@Component
@Profile("self-injection")
class SelfInjectionDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final SelfInjectedCheckoutService checkout;
    private final OrderRepository orders;

    SelfInjectionDemo(SelfInjectedCheckoutService checkout, OrderRepository orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> the demo's field holds: " + checkout.getClass().getName());
        System.out.println(">>> checkout.createOrder(...), which calls self.persistOrder(...)");
        try {
            checkout.createOrder(SUFI_NIGHT, "self@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());
    }
}
