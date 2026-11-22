package com.zenith.data;

import org.springframework.data.domain.Sort;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

// Lesson 5: a small contract on purpose - only what an "upcoming events" screen needs.
// Repository itself adds no methods, so this interface has exactly the two below.
public interface UpcomingEvents extends Repository<EventEntity, UUID> {

    List<EventEntity> findAll(Sort sort);   // same name and parameters as a method of SimpleJpaRepository

    long count();                           // same here
}
