package com.zenith.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 4: @PreDestroy — the exit hook. The full arc in one run.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l4-predestroy
@Component
@Profile("l4-predestroy")
class L4BookingCounter implements CommandLineRunner {

    L4BookingCounter() {
        System.out.println(">>> constructor:    the object exists");
    }

    @PostConstruct
    void prepare() {
        System.out.println(">>> @PostConstruct: setup done — pretend we opened a connection");
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> run():          the application is doing its work");
    }

    @PreDestroy
    void shutDown() {
        System.out.println(">>> @PreDestroy:    closing what we opened — goodbye");
    }
}
