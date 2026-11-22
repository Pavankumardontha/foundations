package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Lesson 10: the JPQL query, called for a venue with events and a venue without.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=jpql
@Component
@Profile("jpql")
class JpqlDemo implements CommandLineRunner {

    private static final UUID BLUE_NOTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LAKESIDE_HALL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final EventRepository events;

    JpqlDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        show("The Blue Note", BLUE_NOTE);
        show("Lakeside Hall", LAKESIDE_HALL);
    }

    private void show(String venueName, UUID venueId) {
        List<EventEntity> atVenue = events.eventsAtVenueJpql(venueId);
        System.out.println(">>> eventsAtVenueJpql(" + venueName + ") -> " + atVenue.size() + " events");
        for (EventEntity e : atVenue) {
            System.out.println(">>>   " + e.getStartsAt() + "  " + e.getName());
        }
    }
}
