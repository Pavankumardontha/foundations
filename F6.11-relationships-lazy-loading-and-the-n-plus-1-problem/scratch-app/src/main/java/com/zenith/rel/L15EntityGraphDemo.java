package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Lesson 15: the SAME 20 events, fixed a second way - @EntityGraph, no JPQL.
// Expectation: 1 statement, exactly like join fetch.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l15-entity-graph
@Component
@Profile("l15-entity-graph")
class L15EntityGraphDemo implements CommandLineRunner {

    private final EventRepository events;

    L15EntityGraphDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        System.out.println(">>> loading events with @EntityGraph(\"venue\") (watch: 1 select) ...");
        List<EventEntity> demo = events.findWithGraphByNameStartingWithOrderByNameAsc("Load Event ");
        System.out.println(">>> got " + demo.size() + " events. now their venue names (watch: NO new selects) ...");

        List<EventSummary> summaries = new ArrayList<>();
        for (EventEntity event : demo) {
            summaries.add(new EventSummary(event.getId(), event.getName(),
                    event.getStartsAt(), event.getVenue().getName()));   // venue already loaded: free
        }

        System.out.println(">>> built " + summaries.size() + " summaries. first one:");
        System.out.println(">>> " + summaries.get(0));
        System.out.println(">>> statements expected: 1 (the graph pulled the venues along)");
    }
}
