package com.zenith.rel;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

// Spring Data writes the implementation at startup; we only declare the interface.
public interface EventRepository extends JpaRepository<EventEntity, UUID> {

    // Lesson 13: derived query - "where name like ? || '%' order by name".
    // Picks out the twenty N+1 demo events.
    List<EventEntity> findByNameStartingWithOrderByNameAsc(String prefix);

    // Lesson 14: the N+1 fix - one query that also brings each event's venue.
    // "join fetch e.venue" tells Hibernate to load the venue IN THE SAME SELECT,
    // so the venue field arrives already filled - no proxy, no per-row query.
    @Query("select e from EventEntity e join fetch e.venue "
            + "where e.name like concat(:prefix, '%') order by e.name")
    List<EventEntity> findWithVenueByNamePrefix(@Param("prefix") String prefix);

    // Lesson 15: the SAME fix without writing JPQL. The method name stays a plain
    // derived query (Spring Data ignores the "WithGraph" subject before "By");
    // @EntityGraph tells Spring Data "also load venue in this query".
    // attributePaths names the relationship field(s) to fetch eagerly, just here.
    @EntityGraph(attributePaths = "venue")
    List<EventEntity> findWithGraphByNameStartingWithOrderByNameAsc(String prefix);
}
