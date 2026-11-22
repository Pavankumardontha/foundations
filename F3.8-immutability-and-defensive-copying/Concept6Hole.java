import java.util.ArrayList;
import java.util.List;

public class Concept6Hole {

    // A plain record. Fields are private final automatically — and still not safe.
    record Booking(String id, List<String> seats) {}

    public static void main(String[] args) {
        List<String> mine = new ArrayList<>(List.of("A1"));
        Booking booking = new Booking("B-1", mine);
        System.out.println("booking starts as: " + booking.seats());

        mine.add("HACKED");                 // attack 1: the way in
        System.out.println("after way-in:      " + booking.seats());

        booking.seats().add("Z99");         // attack 2: the way out
        System.out.println("after way-out:     " + booking.seats());
    }
}
