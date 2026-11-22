package com.zenith.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

// The Java class that stands for the "event" table. One object = one row.
@Entity
@Table(name = "event")   // without this, Hibernate guesses the table name "event_entity"
public class EventEntity {

    @Id
    private UUID id;                  // column: id (the primary key)

    private UUID venueId;             // column: venue_id  (camelCase -> snake_case)
    private String name;              // column: name
    private OffsetDateTime startsAt;  // column: starts_at
    private String slug;              // column: slug

    protected EventEntity() {}        // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public UUID getVenueId() { return venueId; }
    public String getName() { return name; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public String getSlug() { return slug; }

    // Lesson 16: the one field a service may change.
    public void setName(String name) { this.name = name; }
}
