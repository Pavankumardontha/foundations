// Concept 5, step 2: filter — keep the value only if it passes a test.

import java.util.Optional;

public class Concept5Step2 {

    record Event(String name, int ticketsLeft) {}

    static Optional<Event> findById(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live", 12));   // tickets available
        }
        if (id.equals("E2")) {
            return Optional.of(new Event("Ed Sheeran Tour", 0));  // sold out
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        // Case 1: full box, test PASSES (12 > 0) -> box stays full.
        Optional<Event> bookable1 = findById("E1").filter(e -> e.ticketsLeft() > 0);
        System.out.println("E1   (12 tickets): " + bookable1);

        // Case 2: full box, test FAILS (0 > 0 is false) -> box becomes empty.
        Optional<Event> bookable2 = findById("E2").filter(e -> e.ticketsLeft() > 0);
        System.out.println("E2   (sold out)  : " + bookable2);

        // Case 3: empty box -> test never runs, stays empty.
        Optional<Event> bookable3 = findById("E999").filter(e -> e.ticketsLeft() > 0);
        System.out.println("E999 (not found) : " + bookable3);
    }
}
