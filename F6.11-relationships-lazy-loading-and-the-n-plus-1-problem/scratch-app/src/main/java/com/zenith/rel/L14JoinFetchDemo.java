package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Lesson 14: the SAME 20 events as Lesson 13, now via a join-fetch query.
// Expectation: 1 statement, not 21.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l14-join-fetch
@Component
@Profile("l14-join-fetch")
class L14JoinFetchDemo implements CommandLineRunner {

    private final EventRepository events;

    L14JoinFetchDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(String... args) {
        System.out.println(">>> loading events WITH venue in one query (watch: 1 select) ...");
        List<EventEntity> demo = events.findWithVenueByNamePrefix("Load Event ");
        System.out.println(">>> got " + demo.size() + " events. now their venue names (watch: NO new selects) ...");

        List<EventSummary> summaries = new ArrayList<>();
        for (EventEntity event : demo) {
            summaries.add(new EventSummary(event.getId(), event.getName(),
                    event.getStartsAt(), event.getVenue().getName()));   // venue already loaded: free
        }

        System.out.println(">>> built " + summaries.size() + " summaries. first one:");
        System.out.println(">>> " + summaries.get(0));
        System.out.println(">>> statements expected: 1 (list already carries the venues)");
    }
}
