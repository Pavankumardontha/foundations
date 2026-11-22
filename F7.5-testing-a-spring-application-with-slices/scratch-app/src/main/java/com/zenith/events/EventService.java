package com.zenith.events;

import java.util.UUID;

import org.springframework.stereotype.Service;

// The service layer: looks events up and creates them. The controller stays thin.
@Service
class EventService {

    private final EventRepository repository;

    EventService(EventRepository repository) {
        this.repository = repository;
    }

    EventResponse byId(UUID id) {
        EventEntity entity = repository.findById(id)
                .orElseThrow(EventNotFoundException::new);
        return toResponse(entity);
    }

    EventResponse create(CreateEventRequest request) {
        EventEntity saved = repository.save(EventEntity.of(
                request.name(), request.venueId(), request.salesOpenAt(),
                request.startsAt(), request.capacity()));
        return toResponse(saved);
    }

    private static EventResponse toResponse(EventEntity e) {
        return new EventResponse(e.id(), e.name(), e.venueId(),
                e.salesOpenAt(), e.startsAt(), e.capacity());
    }
}
