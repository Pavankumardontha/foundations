package com.zenith.rel;

import org.hibernate.LazyInitializationException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 9: touch a lazy relationship AFTER the transaction ended. It fails.
// Note: NO @Transactional on run() - that is the whole point.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l9-break
@Component
@Profile("l9-break")
class L9BreakDemo implements CommandLineRunner {

    private final L9EventService service;

    L9BreakDemo(L9EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        UUID monsoonId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        EventEntity event = service.loadEvent(monsoonId);
        System.out.println(">>> back in the runner: transaction over, session closed");

        System.out.println(">>> event name (already loaded): " + event.getName());
        System.out.println(">>> venue id (proxy, no query):  " + event.getVenue().getId());

        System.out.println(">>> venue name (needs a query) ...");
        try {
            System.out.println(">>> venue name: " + event.getVenue().getName());
        } catch (LazyInitializationException e) {
            System.out.println(">>> CRASH:   " + e.getClass().getName());
            System.out.println(">>> message: " + e.getMessage());
        }
    }
}
