import java.util.ArrayList;
import java.util.List;

public class Concept7 {

    record Seat(String id) {}

    // The record's reference to the list is final. The LIST ITSELF is not.
    record BookingLeaky(String eventId, List<Seat> seats) {}

    // The fix (from F3.8): copy on the way in, inside the compact constructor.
    record BookingSafe(String eventId, List<Seat> seats) {
        BookingSafe {
            seats = List.copyOf(seats);   // snapshot; List.copyOf is also unmodifiable
        }
    }

    public static void main(String[] args) {
        // --- the leaky version ---
        List<Seat> chosen = new ArrayList<>(List.of(new Seat("A1"), new Seat("A2")));
        BookingLeaky leaky = new BookingLeaky("EVT-1", chosen);
        System.out.println("leaky before: " + leaky);

        chosen.add(new Seat("Z99"));                 // attack 1: the caller's kept reference
        leaky.seats().add(new Seat("Z100"));         // attack 2: the accessor hands out the list
        System.out.println("leaky after:  " + leaky);

        // --- the safe version ---
        List<Seat> chosen2 = new ArrayList<>(List.of(new Seat("A1"), new Seat("A2")));
        BookingSafe safe = new BookingSafe("EVT-1", chosen2);
        System.out.println("safe before:  " + safe);

        chosen2.add(new Seat("Z99"));                // attack 1 again
        System.out.println("safe after:   " + safe);

        try {
            safe.seats().add(new Seat("Z100"));      // attack 2 again
        } catch (UnsupportedOperationException e) {
            System.out.println("accessor attack: UnsupportedOperationException — list is unmodifiable");
        }
    }
}
