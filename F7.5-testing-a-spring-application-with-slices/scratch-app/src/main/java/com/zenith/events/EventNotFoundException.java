package com.zenith.events;

// Thrown when an event id does not exist. The handler turns this into a 404.
class EventNotFoundException extends DomainException {

    EventNotFoundException() {
        super("event_not_found", "Event not found");
    }
}
