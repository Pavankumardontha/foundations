package com.zenith.catalog;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    private final EventService service;

    public EventController(EventService service) {   // the door; Spring fills it
        this.service = service;
    }

    @GetMapping("/events")
    public List<String> events() {
        return service.upcomingEventNames();
    }
}
