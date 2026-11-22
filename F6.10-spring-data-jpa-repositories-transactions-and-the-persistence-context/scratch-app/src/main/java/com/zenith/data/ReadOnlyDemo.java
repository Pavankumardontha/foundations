package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 18: a normal transaction next to a read-only one, then a rename in a read-only one.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=readonly
@Component
@Profile("readonly")
class ReadOnlyDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    ReadOnlyDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. a normal transaction");
        System.out.println(">>>    " + service.inspectNormalTransaction(SUFI_NIGHT));

        System.out.println(">>> 2. a read-only transaction");
        System.out.println(">>>    " + service.inspectReadOnlyTransaction(SUFI_NIGHT));

        System.out.println(">>> 3. calling renameInReadOnlyTransaction");
        service.renameInReadOnlyTransaction(SUFI_NIGHT, "Sufi Night Live");
        System.out.println(">>>    renameInReadOnlyTransaction returned");

        System.out.println(">>> 4. reading the name from the database again");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));
    }
}
