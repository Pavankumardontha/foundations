package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 3: follow one call from your code, through the proxy, to the target.
// The target refuses a null id, so the app stops with a stack trace that shows the whole path.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=call-path
@Component
@Profile("call-path")
class CallPathDemo implements CommandLineRunner {

    private final EventRepository events;

    CallPathDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> calling findById(null) on purpose - read the stack trace");
        events.findById(null);
    }
}
