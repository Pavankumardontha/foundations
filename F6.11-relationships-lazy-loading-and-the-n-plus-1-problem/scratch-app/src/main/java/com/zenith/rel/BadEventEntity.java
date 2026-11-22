package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.UUID;

// Lesson 16: the WRONG way, on purpose. @Data generates toString() over EVERY field,
// including the lazy `venue`. Observe it misbehave; never copy this onto a real entity.
@Data
@Entity
@Table(name = "event")
public class BadEventEntity {

    @Id
    private UUID id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private VenueEntity venue;
}
