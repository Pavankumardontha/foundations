package com.zenith.rel;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Lesson 10: the Lesson 9 anti-pattern, over HTTP - both endpoints are wrong on purpose.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l10-http
@RestController
@Profile("l10-http")
class L10EventController {

    private final EventRepository events;

    L10EventController(EventRepository events) {
        this.events = events;
    }

    // Anti-pattern A: hand the entity itself to the JSON writer.
    @GetMapping("/events/{id}")
    EventEntity byId(@PathVariable UUID id) {
        return events.findById(id).orElseThrow();
    }

    // Anti-pattern B: touch a lazy relationship in the controller.
    @GetMapping("/events/{id}/venue-name")
    String venueName(@PathVariable UUID id) {
        EventEntity event = events.findById(id).orElseThrow();
        System.out.println(">>> controller: repository call done, its transaction is OVER");
        return event.getVenue().getName();
    }
}
