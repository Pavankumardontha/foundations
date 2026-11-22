// Concept 2, broken on purpose: try to use the Optional as if it were the Event.
// This does NOT compile — which is exactly the point.

import java.util.Optional;

public class Concept2Broken {

    record Event(String name) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        Optional<Event> e = findById("E999");
        System.out.println(e.name()); // compile error: Optional has no name()
    }
}
