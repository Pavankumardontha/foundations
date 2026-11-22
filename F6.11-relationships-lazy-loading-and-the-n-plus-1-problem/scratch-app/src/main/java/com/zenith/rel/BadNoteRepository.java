package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

// Lesson 17: repository for the DB-generated-id demo entity.
public interface BadNoteRepository extends JpaRepository<BadNoteEntity, Long> {
}
