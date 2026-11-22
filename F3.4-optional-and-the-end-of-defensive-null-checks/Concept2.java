// Concept 2: Optional<T> — a box that holds either one value or nothing.
// Same lookup as Concept 1, but now the "might be missing" is in the type.

import java.util.Optional;

public class Concept2 {

    record Event(String name) {}

    // The signature now tells the truth:
    // "I return a box. It may contain an Event, or it may be empty."
    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live")); // box with a value
        }
        return Optional.empty();                            // box with nothing
    }

    public static void main(String[] args) {
        Optional<Event> found = findById("E1");
        Optional<Event> missing = findById("E999");

        // Just print the boxes to see what they look like:
        System.out.println("found   = " + found);
        System.out.println("missing = " + missing);

        // No crash! An empty Optional is a real object, not null.
        // Printing it, passing it around — all safe.
    }
}
