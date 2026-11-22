package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 27: the third fix. createOrder calls this.persistOrder(...) inside a TransactionTemplate
// callback, and persistOrder fails after the save.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=template
@Component
@Profile("template")
class TemplateDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final TemplateCheckoutService checkout;
    private final OrderRepository orders;

    TemplateDemo(TemplateCheckoutService checkout, OrderRepository orders) {
        this.checkout = checkout;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> the demo's field holds: " + checkout.getClass().getName());
        System.out.println(">>> checkout.createOrder(...), with this.persistOrder(...) inside the template");
        try {
            checkout.createOrder(SUFI_NIGHT, "template@example.com", true);
        } catch (RuntimeException e) {
            System.out.println(">>>    caught " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());
    }
}
