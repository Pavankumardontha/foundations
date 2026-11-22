package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 22: a checked exception halfway, first without and then with rollbackFor.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=checked
@Component
@Profile("checked")
class CheckedDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final OrderService service;
    private final OrderRepository orders;

    CheckedDemo(OrderService service, OrderRepository orders) {
        this.service = service;
        this.orders = orders;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. placeTwoOrdersChecked: a checked exception halfway");
        try {
            service.placeTwoOrdersChecked(SUFI_NIGHT, true);
        } catch (PaymentDeclinedException e) {
            System.out.println(">>>    caught PaymentDeclinedException: " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 2. placeTwoOrdersRollbackFor: the same, with rollbackFor");
        try {
            service.placeTwoOrdersRollbackFor(SUFI_NIGHT, true);
        } catch (PaymentDeclinedException e) {
            System.out.println(">>>    caught PaymentDeclinedException: " + e.getMessage());
        }
        System.out.println(">>>    orders in the table: " + orders.count());

        System.out.println(">>> 3. one more order, to see which id comes next");
        long id = service.placeOrder(SUFI_NIGHT, "meera@example.com", 2);
        System.out.println(">>>    placeOrder returned id " + id);
    }
}
