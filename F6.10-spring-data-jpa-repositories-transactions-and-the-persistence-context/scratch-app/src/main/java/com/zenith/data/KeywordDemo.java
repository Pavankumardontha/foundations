package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

// Lesson 7: one query with two keywords, called with the same word in two spellings.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=keywords
@Component
@Profile("keywords")
class KeywordDemo implements CommandLineRunner {

    private final EventRepository events;

    KeywordDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        show("night");
        show("NIGHT");
    }

    private void show(String part) {
        List<EventEntity> found = events.findByNameContainingIgnoreCase(part);
        System.out.println(">>> findByNameContainingIgnoreCase(\"" + part + "\") -> " + found.size() + " events");
        for (EventEntity e : found) {
            System.out.println(">>>   " + e.getName());
        }
    }
}
