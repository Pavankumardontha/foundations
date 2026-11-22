import java.util.ArrayList;
import java.util.List;

public class Concept8 {

    enum Status { DRAFT, PUBLISHED, CANCELLED }

    // ENTITY: has an identity (id) and state that changes over its life —
    // but ONLY through intention-revealing methods. No setters anywhere.
    static final class Event {
        private final String id;
        private Status status = Status.DRAFT;
        private final List<String> heldSeats = new ArrayList<>();

        Event(String id) {
            this.id = id;
        }

        void publish() {
            if (status != Status.DRAFT) {
                throw new IllegalStateException(
                    "only a DRAFT event can be published, not " + status);
            }
            status = Status.PUBLISHED;
        }

        void hold(List<String> seatIds) {
            if (status != Status.PUBLISHED) {
                throw new IllegalStateException(
                    "can only hold seats on a PUBLISHED event, not " + status);
            }
            heldSeats.addAll(seatIds);
        }

        Status status()           { return status; }
        List<String> heldSeats()  { return List.copyOf(heldSeats); }  // copy OUT
    }

    public static void main(String[] args) {
        Event event = new Event("E-1");

        try {
            event.hold(List.of("A1"));          // holding seats on a DRAFT event
        } catch (IllegalStateException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        event.publish();                        // the legal path
        event.hold(List.of("A1", "A2"));
        System.out.println("status: " + event.status() + ", held: " + event.heldSeats());

        try {
            event.publish();                    // publishing twice
        } catch (IllegalStateException e) {
            System.out.println("rejected: " + e.getMessage());
        }
    }
}
