import java.util.List;

class EventRepository {
    private final String databaseUrl;

    EventRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    List<String> findAllNames() {
        // Pretend this line queries the database at databaseUrl.
        return List.of("names fetched from " + databaseUrl);
    }
}

class EventService {
    // The trap: the service builds its own helper. The choice is baked in.
    private final EventRepository repository =
            new EventRepository("jdbc:postgresql://localhost:5432/zenith");

    List<String> upcomingEventNames() {
        return repository.findAllNames();
    }
}

public class Lesson2 {
    public static void main(String[] args) {
        EventService service = new EventService();
        System.out.println(service.upcomingEventNames());
    }
}
