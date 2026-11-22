// Concept 3: the three ways to create an Optional —
// of (definitely a value), empty (definitely nothing), ofNullable (maybe).

import java.util.Optional;

public class Concept3 {

    record Event(String name) {}

    public static void main(String[] args) {
        // 1. Optional.of(value) — "I am CERTAIN this value exists."
        Optional<Event> a = Optional.of(new Event("Coldplay Live"));
        System.out.println("of          : " + a);

        // 2. Optional.empty() — "I am CERTAIN there is nothing."
        Optional<Event> b = Optional.empty();
        System.out.println("empty       : " + b);

        // 3. Optional.ofNullable(value) — "this might be null, you decide."
        Event fromOldCode = null; // e.g. a legacy API that returns null
        Optional<Event> c = Optional.ofNullable(fromOldCode);
        System.out.println("ofNullable null  : " + c);

        Event real = new Event("Ed Sheeran Tour");
        Optional<Event> d = Optional.ofNullable(real);
        System.out.println("ofNullable value : " + d);

        // Now break it on purpose: of() with a null.
        System.out.println("--- breaking Optional.of(null) ---");
        Event nothing = null;
        Optional<Event> boom = Optional.of(nothing); // throws immediately
        System.out.println("never reached: " + boom);
    }
}
