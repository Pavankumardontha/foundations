package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

// Lesson 5: the same code, run once with Spring's repository and once with a hand-written fake.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=narrow
@Component
@Profile("narrow")
class NarrowDemo implements CommandLineRunner {

    private final UpcomingEvents upcoming;

    NarrowDemo(UpcomingEvents upcoming) {   // Spring passes in its proxy for UpcomingEvents
        this.upcoming = upcoming;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> real: " + report(upcoming));
        System.out.println(">>> fake: " + report(new FakeUpcomingEvents()));
    }

    // Works with any object that implements UpcomingEvents.
    static String report(UpcomingEvents source) {
        List<EventEntity> sorted = source.findAll(Sort.by("startsAt"));
        String first = sorted.isEmpty() ? "none" : sorted.get(0).getName();
        return source.getClass().getSimpleName() + " -> " + source.count() + " events, first: " + first;
    }
}
