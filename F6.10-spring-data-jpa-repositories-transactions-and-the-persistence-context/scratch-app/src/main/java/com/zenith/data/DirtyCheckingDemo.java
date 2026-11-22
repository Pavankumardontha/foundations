package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 16: rename an event without calling save(), then read it back to see the database changed.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=dirty
@Component
@Profile("dirty")
class DirtyCheckingDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    DirtyCheckingDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. reading the name from the database");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));

        System.out.println(">>> 2. calling renameWithoutSave");
        service.renameWithoutSave(SUFI_NIGHT, "Sufi Night Live");
        System.out.println(">>>    renameWithoutSave returned");

        System.out.println(">>> 3. reading the name from the database again");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));

        System.out.println(">>> 4. putting the old name back");
        service.renameWithoutSave(SUFI_NIGHT, "Sufi Night");
        System.out.println(">>>    " + service.currentName(SUFI_NIGHT));
    }
}
