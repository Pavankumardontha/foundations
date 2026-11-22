package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 10 demo. Run with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-skip
@Component
@Profile("l10-skip")
public class L10SkipDemo implements CommandLineRunner {

    private final PaymentService paymentService;

    public L10SkipDemo(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void run(String... args) {
        System.out.println("first call returned:  " + paymentService.charge());
        System.out.println("second call returned: " + paymentService.charge());
        System.out.println("the card was charged " + paymentService.timesCharged() + " time(s)");
    }
}
