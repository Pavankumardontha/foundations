import java.util.List;

interface EventRepository {
    List<String> findAllNames();
}

class JdbcEventRepository implements EventRepository {
    private final String databaseUrl;

    JdbcEventRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    @Override
    public List<String> findAllNames() {
        // Pretend this line queries the database at databaseUrl.
        return List.of("names fetched from " + databaseUrl);
    }
}

class FakeEventRepository implements EventRepository {
    @Override
    public List<String> findAllNames() {
        return List.of("Fake Gig", "Fake Festival");
    }
}

class EventService {
    private final EventRepository repository;   // the interface, not a class

    EventService(EventRepository repository) {
        this.repository = repository;
    }

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

public class Lesson3 {
    public static void main(String[] args) {
        EventService real = new EventService(
                new JdbcEventRepository("jdbc:postgresql://localhost:5432/zenith"));
        System.out.println("real: " + real.upcomingEventNames());

        EventService test = new EventService(new FakeEventRepository());
        System.out.println("test: " + test.upcomingEventNames());
    }
}
