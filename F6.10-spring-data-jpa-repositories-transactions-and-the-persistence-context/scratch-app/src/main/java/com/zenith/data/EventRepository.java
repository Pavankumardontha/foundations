package com.zenith.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

// The repository: an interface we declare and never implement.
// <EventEntity, UUID> = what it stores, and the type of that entity's @Id field.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 6: a derived query. No SQL, no body - Spring Data builds the query from the name.
    List<EventEntity> findByVenueId(UUID venueId);

    // Lesson 7: keywords after the property change the condition.
    // Containing = "the name has this text somewhere"; IgnoreCase = "capital letters don't matter".
    List<EventEntity> findByNameContainingIgnoreCase(String part);

    // Lesson 8: a yes/no query. The subject "exists" (instead of "find") makes it return true or false.
    boolean existsBySlug(String slug);

    // Lesson 10: a hand-written query in JPQL. It names the entity class and its fields, not the table and columns.
    @Query("select e from EventEntity e where e.venueId = :venueId order by e.startsAt")
    List<EventEntity> eventsAtVenueJpql(UUID venueId);

    // Lesson 11: the same query in native SQL. It names the table and its columns, exactly as Postgres knows them.
    @Query(value = "select * from event where venue_id = :venueId order by starts_at", nativeQuery = true)
    List<EventEntity> eventsAtVenueNative(UUID venueId);

    // Lesson 13: a Slice is a page without totals, so Spring Data never runs the count query.
    // Nothing after "By" means no condition: every event, one slice at a time.
    Slice<EventEntity> findAllBy(Pageable pageable);
}
