package com.zenith.rel;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Lesson 12: the correct replacement for Lesson 10's broken endpoints.
// The controller never sees an entity - only the finished record.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l12-records
@RestController
@Profile("l12-records")
class L12EventController {

    private final L12EventService service;

    L12EventController(L12EventService service) {
        this.service = service;
    }

    @GetMapping("/v2/events/{id}")
    EventSummary byId(@PathVariable UUID id) {
        return service.eventSummary(id);
    }
}
