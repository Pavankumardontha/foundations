package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Lesson 6: one derived query, called for two venues.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=derived
@Component
@Profile("derived")
class DerivedQueryDemo implements CommandLineRunner {

    private static final UUID BLUE_NOTE     = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID LAKESIDE_HALL = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final EventRepository events;

    DerivedQueryDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        show("The Blue Note", BLUE_NOTE);
        show("Lakeside Hall", LAKESIDE_HALL);
    }

    private void show(String venueName, UUID venueId) {
        List<EventEntity> atVenue = events.findByVenueId(venueId);
        System.out.println(">>> findByVenueId(" + venueName + ") -> " + atVenue.size() + " events");
        for (EventEntity e : atVenue) {
            System.out.println(">>>   " + e.getName());
        }
    }
}
