package com.zenith.autoconfig;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// A minimal entity, so the app genuinely uses JPA.
@Entity
public class Note {

    @Id
    private Long id;

    private String text;

    protected Note() {
        // JPA requires a no-args constructor.
    }
}
