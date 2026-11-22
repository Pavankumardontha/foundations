package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 15: load one event twice - without and with a transaction - and count the SELECTs.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=pc
@Component
@Profile("pc")
class PersistenceContextDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    PersistenceContextDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. loadTwiceWithoutTransaction");
        System.out.println(">>>    " + service.loadTwiceWithoutTransaction(SUFI_NIGHT));

        System.out.println(">>> 2. loadTwiceInOneTransaction");
        System.out.println(">>>    " + service.loadTwiceInOneTransaction(SUFI_NIGHT));
    }
}
