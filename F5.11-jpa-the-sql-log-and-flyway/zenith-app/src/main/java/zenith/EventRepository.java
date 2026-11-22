package zenith;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

// F5.11 Lesson 5: the repository - an interface we declare and never implement.
// Spring Data JPA builds the working object at startup.
public interface EventRepository extends JpaRepository<Event, UUID> {

    // F5.11 Lesson 7 (subtask D): a derived query method - the NAME is the query.
    // findBy + Name (the entity field) + Containing (LIKE %?%) + IgnoreCase (upper both sides).
    List<Event> findByNameContainingIgnoreCase(String q);

    // F5.11 Lesson 12 (subtask F): exactly five events, in one query.
    List<Event> findTop5ByOrderByNameAsc();
}
