package com.zenith.lifecycle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 8: print what the context actually handed us, then use it.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-transactional
@Component
@Profile("l8-transactional")
class L8ProxyMeet implements CommandLineRunner {

    private final L8PlainService plain;
    private final L8BookingService booking;

    L8ProxyMeet(L8PlainService plain, L8BookingService booking) {
        this.plain = plain;
        this.booking = booking;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> plain service class  : " + plain.getClass().getName());
        System.out.println(">>> booking service class: " + booking.getClass().getName());
        System.out.println(">>> ping(): " + plain.ping());
        System.out.println(">>> reserveSeat(): " + booking.reserveSeat());
    }
}
