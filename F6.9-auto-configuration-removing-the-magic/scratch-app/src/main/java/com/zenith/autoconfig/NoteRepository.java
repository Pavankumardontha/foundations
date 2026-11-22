package com.zenith.autoconfig;

import org.springframework.data.jpa.repository.JpaRepository;

// A repository bean. Spring Data builds it at startup,
// and building it REQUIRES the JPA machinery (entityManagerFactory).
public interface NoteRepository extends JpaRepository<Note, Long> {
}
