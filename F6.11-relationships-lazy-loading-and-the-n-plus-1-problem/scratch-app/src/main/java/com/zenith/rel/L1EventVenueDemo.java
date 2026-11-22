package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 1: the old way (two hand-written queries) next to the new way (@ManyToOne).
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l1-event-venue
@Component
@Profile("l1-event-venue")
class L1EventVenueDemo implements CommandLineRunner {

    private final JdbcClient jdbc;
    private final EventRepository events;

    L1EventVenueDemo(JdbcClient jdbc, EventRepository events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @Override
    public void run(String... args) {
        UUID monsoonId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

        // ---- Part 1: the old way. The row gives us only venue_id, a bare UUID.
        //      To learn the venue's name we must write a second query ourselves.
        System.out.println(">>> OLD WAY -------------------------------------------");
        UUID venueId = jdbc.sql("select venue_id from event where id = ?")
                .param(monsoonId)
                .query(UUID.class)
                .single();
        String venueName = jdbc.sql("select name from venue where id = ?")
                .param(venueId)
                .query(String.class)
                .single();
        System.out.println(">>> venue_id from event row: " + venueId);
        System.out.println(">>> venue name after 2nd query: " + venueName);

        // ---- Part 2: the new way. The entity now holds a VenueEntity object.
        //      We just walk from the event to its venue. No query written by us.
        System.out.println(">>> NEW WAY -------------------------------------------");
        EventEntity event = events.findById(monsoonId).orElseThrow();
        System.out.println(">>> event: " + event.getName());
        System.out.println(">>> venue: " + event.getVenue().getName()
                + " (" + event.getVenue().getCity() + ")");
    }
}
