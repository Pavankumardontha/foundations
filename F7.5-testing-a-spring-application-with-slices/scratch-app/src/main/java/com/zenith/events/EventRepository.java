package com.zenith.events;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // A derived query: Spring Data writes the SQL from the method name.
    List<EventEntity> findByVenueIdOrderByStartsAtAsc(UUID venueId);
}
