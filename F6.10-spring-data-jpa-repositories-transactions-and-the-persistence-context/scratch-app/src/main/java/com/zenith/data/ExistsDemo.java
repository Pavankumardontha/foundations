package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 8: a yes/no question to the database - "is there an event with this slug?"
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=exists
@Component
@Profile("exists")
class ExistsDemo implements CommandLineRunner {

    private final EventRepository events;

    ExistsDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        check("sufi-night");
        check("rain-dance");
        check("Sufi-Night");
    }

    private void check(String slug) {
        boolean taken = events.existsBySlug(slug);
        System.out.println(">>> existsBySlug(\"" + slug + "\") -> " + taken);
    }
}
