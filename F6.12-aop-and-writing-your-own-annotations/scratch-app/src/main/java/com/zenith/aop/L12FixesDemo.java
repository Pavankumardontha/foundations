package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 12 demo. Run with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l12-fixes
@Component
@Profile("l12-fixes")
public class L12FixesDemo implements CommandLineRunner {

    private final CheckoutServiceFixed checkoutServiceFixed;
    private final SelfInjectedCheckoutService selfInjectedCheckoutService;

    public L12FixesDemo(CheckoutServiceFixed checkoutServiceFixed,
                        SelfInjectedCheckoutService selfInjectedCheckoutService) {
        this.checkoutServiceFixed = checkoutServiceFixed;
        this.selfInjectedCheckoutService = selfInjectedCheckoutService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- fix 1: annotated method moved to its own bean ---");
        checkoutServiceFixed.checkout();

        System.out.println("--- fix 2: bean injected into itself ---");
        selfInjectedCheckoutService.checkout();
    }
}
