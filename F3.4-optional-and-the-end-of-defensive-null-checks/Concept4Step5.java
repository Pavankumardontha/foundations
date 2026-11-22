// Concept 4, step 5: get() — the method you must never use.

import java.util.Optional;

public class Concept4Step5 {

    record Event(String name) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        // get() on a FULL box: works. This is what tempts people.
        Event good = findById("E1").get();
        System.out.println("full box, get() gave: " + good.name());

        // get() on an EMPTY box: crash.
        Event bad = findById("E999").get();
        System.out.println("never reached: " + bad.name());
    }
}
