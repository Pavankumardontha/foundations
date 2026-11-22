// Concept 9: the real Phase 1 line, end to end.
// repository.findById(id).map(Event::name).orElseThrow(() -> new EventNotFoundException(id));

import java.util.Optional;

public class Concept9 {

    record Event(String name) {}

    // Our own error type, with a message that names the missing id.
    static class EventNotFoundException extends RuntimeException {
        EventNotFoundException(String id) {
            super("Event not found: " + id);
        }
    }

    // A tiny "repository" — in Phase 1 this will be a real one backed by a database.
    static class EventRepository {
        Optional<Event> findById(String id) {
            if (id.equals("E1")) {
                return Optional.of(new Event("Coldplay Live"));
            }
            return Optional.empty();
        }
    }

    static EventRepository repository = new EventRepository();

    // THE LINE.
    static String eventName(String id) {
        return repository.findById(id)
                .map(Event::name)
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    public static void main(String[] args) {
        // Case 1: the event exists.
        System.out.println("E1 gave: " + eventName("E1"));

        // Case 2: the event does not exist.
        System.out.println("E999 gives: " + eventName("E999"));
    }
}
