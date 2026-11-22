package com.zenith.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

// Lesson 11 — subtask S6: the repository slice.
// Starts entities + repositories + an embedded database. No web, no services.
// Each test runs inside a transaction that is rolled back afterwards.
@DataJpaTest
class L11RepositorySliceTest {

    @Autowired
    EventRepository repository;

    @Test
    void findsOnlyOurVenueOrderedByStartTime() {
        UUID ourVenue = UUID.randomUUID();
        UUID otherVenue = UUID.randomUUID();

        // Saved out of order on purpose: the late show goes in first.
        repository.save(EventEntity.of("Late show", ourVenue,
                Instant.parse("2030-05-01T10:00:00Z"),
                Instant.parse("2030-06-01T20:00:00Z"), 300));
        repository.save(EventEntity.of("Early show", ourVenue,
                Instant.parse("2030-01-01T10:00:00Z"),
                Instant.parse("2030-02-01T19:00:00Z"), 150));
        repository.save(EventEntity.of("Someone else's show", otherVenue,
                Instant.parse("2030-03-01T10:00:00Z"),
                Instant.parse("2030-04-01T18:00:00Z"), 80));

        List<EventEntity> found = repository.findByVenueIdOrderByStartsAtAsc(ourVenue);

        assertEquals(2, found.size());                      // other venue filtered out
        assertEquals("Early show", found.get(0).name());    // ascending by startsAt
        assertEquals("Late show", found.get(1).name());
    }

    @Test
    void tableIsEmptyAgainBecauseEveryTestRollsBack() {
        assertEquals(0, repository.count());
    }
}
