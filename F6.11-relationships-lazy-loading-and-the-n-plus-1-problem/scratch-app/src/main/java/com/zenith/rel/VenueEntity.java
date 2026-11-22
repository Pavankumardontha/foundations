package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// The Java class that stands for the "venue" table. One object = one row.
@Entity
@Table(name = "venue")
public class VenueEntity {

    @Id
    private UUID id;        // column: id (the primary key)

    private String name;    // column: name
    private String city;    // column: city

    protected VenueEntity() {}   // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
}
