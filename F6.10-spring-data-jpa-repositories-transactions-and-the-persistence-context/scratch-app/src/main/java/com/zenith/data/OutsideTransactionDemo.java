package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 17: the rename from the dirty checking demo, but with no transaction around it.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=outside
@Component
@Profile("outside")
class OutsideTransactionDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    OutsideTransactionDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. reading the name from the database");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));

        System.out.println(">>> 2. calling renameOutsideTransaction");
        service.renameOutsideTransaction(SUFI_NIGHT, "Sufi Night Live");
        System.out.println(">>>    renameOutsideTransaction returned");

        System.out.println(">>> 3. reading the name from the database again");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));
    }
}
