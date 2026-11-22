package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

// Lesson 19: repository for the well-behaved entity.
public interface GoodNoteRepository extends JpaRepository<GoodNoteEntity, UUID> {
}
