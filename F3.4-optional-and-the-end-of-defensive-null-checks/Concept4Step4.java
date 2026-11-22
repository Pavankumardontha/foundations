// Concept 4, step 4: orElseThrow — empty box means ERROR, stop the program.

import java.util.Optional;

public class Concept4Step4 {

    record Event(String name) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        // Full box: works like normal, just gives the value.
        Event good = findById("E1")
                .orElseThrow(() -> new IllegalStateException("No event with id E1"));
        System.out.println("full box gave: " + good.name());

        // Empty box: stops the program with OUR error message.
        Event bad = findById("E999")
                .orElseThrow(() -> new IllegalStateException("No event with id E999"));
        System.out.println("never reached: " + bad.name());
    }
}
