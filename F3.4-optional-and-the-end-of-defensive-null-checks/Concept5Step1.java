// Concept 5, step 1: map — change the value WITHOUT opening the box.

import java.util.Optional;

public class Concept5Step1 {

    record Event(String name) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        // FULL box: map applies the change to the value inside.
        Optional<String> fullName = findById("E1").map(e -> e.name());
        System.out.println("full  box after map: " + fullName);

        // EMPTY box: map quietly does nothing. No crash.
        Optional<String> emptyName = findById("E999").map(e -> e.name());
        System.out.println("empty box after map: " + emptyName);
    }
}
