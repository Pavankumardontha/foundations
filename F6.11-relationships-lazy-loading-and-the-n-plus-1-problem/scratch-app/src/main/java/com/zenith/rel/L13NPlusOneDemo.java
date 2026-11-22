package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Lesson 13: the N+1 problem, live. Everything here follows the project rule
// (map to records inside the transaction) - and still fires 21 statements.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l13-n-plus-1
@Component
@Profile("l13-n-plus-1")
class L13NPlusOneDemo implements CommandLineRunner {

    private final EventRepository events;

    L13NPlusOneDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        System.out.println(">>> loading the demo events (watch: 1 select) ...");
        List<EventEntity> demo = events.findByNameStartingWithOrderByNameAsc("Load Event ");
        System.out.println(">>> got " + demo.size() + " events. now their venue names (watch the log) ...");

        List<EventSummary> summaries = new ArrayList<>();
        for (EventEntity event : demo) {
            summaries.add(new EventSummary(event.getId(), event.getName(),
                    event.getStartsAt(), event.getVenue().getName()));   // lazy touch, once per event
        }

        System.out.println(">>> built " + summaries.size() + " summaries. first one:");
        System.out.println(">>> " + summaries.get(0));
        System.out.println(">>> statements: 1 (event list) + " + summaries.size() + " (one per venue)");
    }
}
