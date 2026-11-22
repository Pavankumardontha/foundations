package com.zenith.rel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// Spring Data writes the implementation at startup; we only declare the interface.
public interface SeatRepository extends JpaRepository<SeatEntity, UUID> {

    // Lesson 4: derived query, used to remove the demo seats (row C) between runs.
    List<SeatEntity> findByRowLabel(String rowLabel);
}
