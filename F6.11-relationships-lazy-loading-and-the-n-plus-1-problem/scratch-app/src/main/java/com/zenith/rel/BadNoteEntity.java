package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

// Lesson 17: the WRONG way, on purpose. @Data builds equals()/hashCode() from EVERY
// field - including this database-generated id, which is null before save and a real
// number after save. That change breaks any hash-based collection across a save.
@Data
@Entity
@Table(name = "demo_note")
public class BadNoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // the DATABASE assigns it on insert
    private Long id;

    private String body;
}
