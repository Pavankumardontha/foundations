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
        // Pretend this queries the database at databaseUrl.
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
    private final EventRepository repository;

    EventService(EventRepository repository) {
        this.repository = repository;
    }

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

class EventController {
    private final EventService service;

    EventController(EventService service) {   // the door the Spring version lacked
        this.service = service;
    }

    List<String> events() {
        return service.upcomingEventNames();
    }
}

public class Lesson6 {
    public static void main(String[] args) {
        // THE WIRING PLACE: every new in the program happens here, deepest first.
        EventRepository repository =
                new JdbcEventRepository("jdbc:postgresql://localhost:5432/zenith");
        EventService service = new EventService(repository);
        EventController controller = new EventController(service);

        System.out.println("app answers: " + controller.events());

        // The test that was impossible in the Spring scratch app, now three lines:
        EventController underTest =
                new EventController(new EventService(new FakeEventRepository()));
        List<String> got = underTest.events();
        System.out.println("test got:    " + got);
        System.out.println(got.equals(List.of("Fake Gig", "Fake Festival"))
                ? "TEST PASSED" : "TEST FAILED");
    }
}
