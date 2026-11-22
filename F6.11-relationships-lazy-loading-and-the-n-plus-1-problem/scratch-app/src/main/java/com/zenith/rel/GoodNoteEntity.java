package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

// Lesson 19: the RIGHT way - the entity hygiene rule this project follows instead of @Data.
//  - @Getter ONLY (no @Data, no @Setter): the outside world reads, never blindly writes.
//  - a STABLE business key (this UUID), assigned in the constructor, never changed.
//  - equals()/hashCode() on that key, using instanceof so a Hibernate PROXY still matches.
//  - an explicit no-arg constructor for Hibernate + a public one that sets the key.
//  - toString() prints scalar fields only - never a relationship.
@Getter
@Entity
@Table(name = "demo_good_note")
public class GoodNoteEntity {

    @Id
    private UUID id;

    private String body;

    protected GoodNoteEntity() {
        // Hibernate builds the object empty, then fills the fields
    }

    public GoodNoteEntity(String body) {
        this.id = UUID.randomUUID();   // stable key, fixed from birth - not from the database
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // instanceof (not getClass() ==) so a proxy subclass still counts as equal
        if (!(o instanceof GoodNoteEntity that)) return false;
        // read the other side's key through the GETTER, so a proxy initializes correctly
        return id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();   // the key never changes, so this never changes
    }

    @Override
    public String toString() {
        return "GoodNoteEntity{id=" + id + ", body=" + body + "}";   // no relationships
    }
}
