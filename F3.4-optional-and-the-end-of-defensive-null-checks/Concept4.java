// Concept 4: getting the value OUT of the box —
// orElse, orElseGet (the expensive-default trap), orElseThrow, and why get() is forbidden.

import java.util.Optional;

public class Concept4 {

    record Event(String name) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live"));
        }
        return Optional.empty();
    }

    // A default that is costly to build. It prints so we can SEE when it runs.
    static Event expensiveDefault() {
        System.out.println("   ...expensiveDefault() is RUNNING (imagine a slow DB call)...");
        return new Event("Default Event");
    }

    public static void main(String[] args) {
        // 1. orElse on an EMPTY box: fallback is used.
        Event e1 = findById("E999").orElse(new Event("Unknown"));
        System.out.println("1. orElse on empty    : " + e1.name());

        // 2. orElse on a FULL box: the real value wins.
        Event e2 = findById("E1").orElse(new Event("Unknown"));
        System.out.println("2. orElse on present  : " + e2.name());

        // 3. THE TRAP — orElse always evaluates its argument,
        //    even when the box is full and the default is thrown away.
        System.out.println("3. orElse(expensiveDefault()) on a PRESENT value:");
        Event e3 = findById("E1").orElse(expensiveDefault());
        System.out.println("   result: " + e3.name());

        // 4. orElseGet takes a FUNCTION, and only calls it if the box is empty.
        System.out.println("4. orElseGet(Concept4::expensiveDefault) on a PRESENT value:");
        Event e4 = findById("E1").orElseGet(Concept4::expensiveDefault);
        System.out.println("   result: " + e4.name());

        // 5. orElseGet on an EMPTY box: now the function runs, because it is needed.
        System.out.println("5. orElseGet on an EMPTY value:");
        Event e5 = findById("E999").orElseGet(Concept4::expensiveDefault);
        System.out.println("   result: " + e5.name());

        // 6. orElseThrow — unwrap, or throw the exception YOU name.
        try {
            findById("E999").orElseThrow(
                    () -> new IllegalStateException("No event with id E999"));
        } catch (IllegalStateException ex) {
            System.out.println("6. orElseThrow threw  : " + ex.getMessage());
        }

        // 7. The forbidden one: get() on an empty box. Crashes.
        System.out.println("7. calling get() on an empty Optional:");
        Event bad = findById("E999").get();
        System.out.println("never reached: " + bad.name());
    }
}
