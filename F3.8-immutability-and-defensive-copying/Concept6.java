import java.util.ArrayList;
import java.util.List;

public class Concept6 {

    // Same record, one line added: copy inside the compact constructor.
    record Booking(String id, List<String> seats) {
        Booking {
            seats = List.copyOf(seats);     // copy IN; this value gets stored
        }
    }

    public static void main(String[] args) {
        List<String> mine = new ArrayList<>(List.of("A1"));
        Booking booking = new Booking("B-1", mine);
        System.out.println("booking starts as: " + booking.seats());

        mine.add("HACKED");                 // attack 1: the way in
        System.out.println("after way-in:      " + booking.seats());

        try {
            booking.seats().add("Z99");     // attack 2: the way out
        } catch (UnsupportedOperationException e) {
            System.out.println("way-out attack failed with: " + e);
        }
        System.out.println("booking is still:  " + booking.seats());
    }
}
