package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

// The Java class that stands for the "role" table. One object = one row.
@Entity
@Table(name = "role")
public class RoleEntity {

    @Id
    private UUID id;        // column: id (the primary key)

    private String name;    // column: name

    protected RoleEntity() {}   // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
}
