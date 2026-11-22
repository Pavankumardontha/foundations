// Concept 6, step 2: flatMap — same as map, but does NOT double-wrap.

import java.util.Optional;

public class Concept6Step2 {

    record Venue(String name) {}
    record Event(String name, String venueId) {}

    static Optional<Event> findEvent(String id) {
        if (id.equals("E1")) {
            return Optional.of(new Event("Coldplay Live", "V7"));   // venue exists
        }
        if (id.equals("E2")) {
            return Optional.of(new Event("Mystery Night", "V404")); // venue missing!
        }
        return Optional.empty();
    }

    static Optional<Venue> findVenue(String venueId) {
        if (venueId.equals("V7")) {
            return Optional.of(new Venue("Wembley Stadium"));
        }
        return Optional.empty();
    }

    public static void main(String[] args) {
        // Same line as step 1, with ONE word changed: map -> flatMap.
        Optional<Venue> venue = findEvent("E1").flatMap(e -> findVenue(e.venueId()));
        System.out.println("E1 venue: " + venue);

        // And now the chain we could not write before just works:
        String name1 = findEvent("E1")
                .flatMap(e -> findVenue(e.venueId()))
                .map(v -> v.name())
                .orElse("unknown venue");
        System.out.println("E1 venue name: " + name1);

        // Event exists, but ITS VENUE is missing -> empty flows through:
        String name2 = findEvent("E2")
                .flatMap(e -> findVenue(e.venueId()))
                .map(v -> v.name())
                .orElse("unknown venue");
        System.out.println("E2 venue name: " + name2);

        // Event itself missing -> empty flows through from the very start:
        String name3 = findEvent("E999")
                .flatMap(e -> findVenue(e.venueId()))
                .map(v -> v.name())
                .orElse("unknown venue");
        System.out.println("E999 venue name: " + name3);
    }
}
