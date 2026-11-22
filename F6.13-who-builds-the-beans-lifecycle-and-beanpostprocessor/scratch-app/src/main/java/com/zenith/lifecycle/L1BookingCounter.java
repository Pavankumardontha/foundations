package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 1: who calls the constructor of a bean, and when.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l1-constructor
@Component
@Profile("l1-constructor")
class L1BookingCounter {

    L1BookingCounter() {
        System.out.println(">>> constructor: Spring is running `new L1BookingCounter()` right now");
    }
}
