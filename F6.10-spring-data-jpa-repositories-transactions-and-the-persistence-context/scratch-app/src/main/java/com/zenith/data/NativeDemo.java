package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Lesson 11: the same question asked twice - once in JPQL, once in native SQL.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=native
@Component
@Profile("native")
class NativeDemo implements CommandLineRunner {

    private static final UUID BLUE_NOTE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final EventRepository events;

    NativeDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        List<String> fromJpql = names(events.eventsAtVenueJpql(BLUE_NOTE));
        List<String> fromNative = names(events.eventsAtVenueNative(BLUE_NOTE));
        System.out.println(">>> JPQL:   " + fromJpql);
        System.out.println(">>> native: " + fromNative);
        System.out.println(">>> same result: " + fromJpql.equals(fromNative));
    }

    private static List<String> names(List<EventEntity> found) {
        List<String> names = new ArrayList<>();
        for (EventEntity e : found) {
            names.add(e.getName());
        }
        return names;
    }
}
