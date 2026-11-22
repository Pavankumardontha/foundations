package com.zenith.events;

// Thrown when a well-formed publish request breaks a business rule.
class EventNotPublishableException extends DomainException {

    EventNotPublishableException(String reason) {
        super("event_not_publishable", "Event cannot be published: " + reason);
    }
}
