package com.zenith.events;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// The event row in the database. Kept deliberately small for this task.
@Entity
@Table(name = "events")
class EventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private UUID venueId;

    @Column(nullable = false)
    private Instant salesOpenAt;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private int capacity;

    protected EventEntity() {
        // JPA needs a no-argument constructor. Code should use of(...) instead.
    }

    static EventEntity of(String name, UUID venueId, Instant salesOpenAt,
                          Instant startsAt, int capacity) {
        EventEntity e = new EventEntity();
        e.name = name;
        e.venueId = venueId;
        e.salesOpenAt = salesOpenAt;
        e.startsAt = startsAt;
        e.capacity = capacity;
        return e;
    }

    UUID id() { return id; }
    String name() { return name; }
    UUID venueId() { return venueId; }
    Instant salesOpenAt() { return salesOpenAt; }
    Instant startsAt() { return startsAt; }
    int capacity() { return capacity; }
}
