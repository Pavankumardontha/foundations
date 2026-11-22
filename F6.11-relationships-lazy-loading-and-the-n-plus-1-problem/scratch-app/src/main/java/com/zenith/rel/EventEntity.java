package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// The Java class that stands for the "event" table. One object = one row.
@Entity
@Table(name = "event")
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at
    private String slug;              // column: slug

    // Lesson 1: the foreign key column becomes an object reference.
    // Many event rows point at one venue row, so: @ManyToOne.
    // Lesson 7: LAZY written out. @ManyToOne defaults to EAGER - the trap -
    // so this project writes the fetch type on every relationship.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")    // the column in THIS table that holds the key
    private VenueEntity venue;

    protected EventEntity() {}        // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public String getSlug() { return slug; }
    public VenueEntity getVenue() { return venue; }
}
