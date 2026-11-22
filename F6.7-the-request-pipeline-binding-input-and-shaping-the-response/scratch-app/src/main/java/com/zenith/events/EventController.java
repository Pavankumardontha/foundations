package com.zenith.events;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
class EventController {

    @GetMapping("/{id}")
    EventResponse byId(@PathVariable("id") UUID eventId) {
        return new EventResponse(eventId, "Indie Night", "Blue Note Hall");
    }

    @GetMapping
    List<EventResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        System.out.println("search ran: X-Request-Id=" + requestId);
        int capped = Math.min(limit, 100);   // server-side cap: no client may demand the world
        String base = (q == null) ? "Event" : q;
        List<EventResponse> results = new ArrayList<>();
        for (int n = 1; n <= capped; n++) {
            results.add(new EventResponse(UUID.randomUUID(), base + " #" + n, "Blue Note Hall"));
        }
        return results;
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@RequestBody CreateEventRequest request) {
        EventResponse created = new EventResponse(UUID.randomUUID(), request.name(), request.venue());
        return ResponseEntity
                .created(URI.create("/events/" + created.id()))
                .body(created);
    }
}
