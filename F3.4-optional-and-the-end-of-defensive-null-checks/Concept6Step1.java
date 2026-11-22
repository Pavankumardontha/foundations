// Concept 6, step 1: the problem flatMap solves — a box stuck inside another box.

import java.util.Optional;

public class Concept6Step1 {

    record Venue(String name) {}
    record Event(String name, String venueId) {}

    // Lookup 1: id -> maybe an Event
    static Optional<Event> findEvent(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live", "V7"));
        }
        return Optional.empty();
    }

    // Lookup 2: venueId -> maybe a Venue (venues can be missing too!)
    static Optional<Venue> findVenue(String venueId) {
        if (venueId.equals("V7")) {
            return Optional.of(new Venue("Wembley Stadium"));
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        // Goal: from event id, get the venue. Try the tool we know: map.
        Optional<Optional<Venue>> nested = findEvent("E1").map(e -> findVenue(e.venueId()));

        System.out.println("what we got: " + nested);
        // A box inside a box. Now we are stuck — see the compile error in step 1b.
    }
}
