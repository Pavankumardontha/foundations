package com.zenith.events;

import java.net.URI;
import java.time.Instant;
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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/events")
class EventController {

    // A fixed venue id, standing in for a real venue lookup until a later task.
    private static final UUID BLUE_NOTE_HALL =
            UUID.fromString("7b4a0f8e-1f7d-4c43-9a30-2f1d7d6c5a01");

    private final EventService service;

    EventController(EventService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    EventResponse byId(@PathVariable("id") UUID eventId) {
        return service.byId(eventId);
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
            results.add(new EventResponse(UUID.randomUUID(), base + " #" + n, BLUE_NOTE_HALL,
                    Instant.parse("2026-12-01T10:00:00Z"),
                    Instant.parse("2026-12-20T19:30:00Z"), 300));
        }
        return results;
    }

    // Stand-in endpoints: real throw sites arrive with the service layer in later tasks.
    // The scratch rules: this app has no price tiers yet, and seat A12 is always taken.
    @PostMapping("/{id}/publish")
    ResponseEntity<Void> publish(@PathVariable("id") UUID eventId) {
        throw new EventNotPublishableException("it has no price tier");
    }

    @PostMapping("/{id}/seats/{seat}")
    ResponseEntity<Void> reserveSeat(@PathVariable("id") UUID eventId,
                                     @PathVariable("seat") String seat) {
        if (seat.equals("A12")) {
            throw new SeatAlreadyReservedException(seat);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventResponse created = service.create(request);
        return ResponseEntity
                .created(URI.create("/events/" + created.id()))
                .body(created);
    }
}
