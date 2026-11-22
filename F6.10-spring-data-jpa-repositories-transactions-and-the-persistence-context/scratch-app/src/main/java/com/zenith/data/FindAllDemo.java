package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

// Lesson 2: call one inherited method and read the SQL it produced.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=find-all
@Component
@Profile("find-all")
class FindAllDemo implements CommandLineRunner {

    private final EventRepository events;

    FindAllDemo(EventRepository events) {   // Spring passes the repository in
        this.events = events;
    }

    @Override
    public void run(String... args) {
        List<EventEntity> all = events.findAll();
        System.out.println(">>> findAll() returned " + all.size() + " events:");
        for (EventEntity e : all) {
            System.out.println(">>>   " + e.getName() + " | venue " + e.getVenueId() + " | " + e.getStartsAt());
        }
    }
}
