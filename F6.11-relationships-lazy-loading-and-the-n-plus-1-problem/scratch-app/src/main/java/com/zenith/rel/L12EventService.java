package com.zenith.rel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 12: the project rule - load, touch, and map INSIDE the transaction;
// return a record, never an entity.
@Service
public class L12EventService {

    private final EventRepository events;

    L12EventService(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public EventSummary eventSummary(UUID id) {
        EventEntity event = events.findById(id).orElseThrow();
        String venueName = event.getVenue().getName();   // lazy touch - the session is alive HERE
        System.out.println(">>> service: record built, transaction still open");
        return new EventSummary(event.getId(), event.getName(), event.getStartsAt(), venueName);
    }
}
