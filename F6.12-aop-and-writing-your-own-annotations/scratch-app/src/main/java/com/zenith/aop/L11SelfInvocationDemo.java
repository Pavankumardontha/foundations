package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 11 demo. Run with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l11-self
@Component
@Profile("l11-self")
public class L11SelfInvocationDemo implements CommandLineRunner {

    private final CheckoutService checkoutService;

    public L11SelfInvocationDemo(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- 1: calling calculateTotal() from OUTSIDE ---");
        checkoutService.calculateTotal();

        System.out.println("--- 2: calling checkout(), which calls this.calculateTotal() INSIDE ---");
        checkoutService.checkout();
    }
}
