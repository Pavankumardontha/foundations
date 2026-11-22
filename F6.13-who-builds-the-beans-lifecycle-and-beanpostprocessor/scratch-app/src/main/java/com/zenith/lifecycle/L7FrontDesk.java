package com.zenith.lifecycle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 7: an ordinary consumer. It asks for a greeter and uses what it gets.
@Component
@Profile("l7-swap")
class L7FrontDesk implements CommandLineRunner {

    private final L7Greeter greeter;

    L7FrontDesk(L7Greeter greeter) {
        this.greeter = greeter;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> front desk got : " + greeter.getClass().getName());
        System.out.println(">>> greeting       : " + greeter.greet());
    }
}
