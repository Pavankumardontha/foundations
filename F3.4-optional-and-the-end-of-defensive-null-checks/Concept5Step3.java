// Concept 5, step 3: ifPresent — DO something with the value, only if there is one.

import java.util.Optional;

public class Concept5Step3 {

    record Event(String name) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        // FULL box: the action runs with the value.
        System.out.println("-- E1 --");
        findById("E1").ifPresent(e -> System.out.println("sending email about: " + e.name()));

        // EMPTY box: the action is skipped. Nothing happens, no crash.
        System.out.println("-- E999 --");
        findById("E999").ifPresent(e -> System.out.println("sending email about: " + e.name()));

        System.out.println("-- done --");
    }
}
