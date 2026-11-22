import java.util.List;

class EventRepository {
    List<String> findAllNames() {
        // Pretend this line talks to a database.
        return List.of("Rock Night", "Jazz Evening");
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

public class Lesson1 {
    public static void main(String[] args) {
        EventRepository repository = new EventRepository();   // step 1: build the helper
        EventService service = new EventService(repository);  // step 2: hand it in
        System.out.println(service.upcomingEventNames());     // step 3: use it
    }
}
