package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Lesson 4: one method from each branch of the family, called on the same repository.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=family
@Component
@Profile("family")
class FamilyDemo implements CommandLineRunner {

    private final EventRepository events;

    FamilyDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        // declared in CrudRepository
        boolean found = events.existsById(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));
        System.out.println(">>> existsById(...0001) -> " + found);

        // declared in ListPagingAndSortingRepository
        List<EventEntity> byStart = events.findAll(Sort.by("startsAt"));
        System.out.println(">>> findAll(Sort.by(\"startsAt\")) -> " + byStart.size() + " events, earliest first:");
        for (EventEntity e : byStart) {
            System.out.println(">>>   " + e.getStartsAt() + " | " + e.getName());
        }
    }
}
