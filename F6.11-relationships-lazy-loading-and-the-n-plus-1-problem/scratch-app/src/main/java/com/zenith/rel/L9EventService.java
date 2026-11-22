package com.zenith.rel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 9: the anti-pattern, on purpose - a service that RETURNS AN ENTITY.
// The transaction, and with it the session, ends when the method returns.
@Service
public class L9EventService {

    private final EventRepository events;

    L9EventService(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public EventEntity loadEvent(UUID id) {
        EventEntity event = events.findById(id).orElseThrow();
        System.out.println(">>> inside the service: transaction open, session open");
        return event;   // <-- commit happens here; the session closes here
    }
}
