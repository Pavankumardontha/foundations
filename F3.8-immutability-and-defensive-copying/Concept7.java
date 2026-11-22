import java.util.ArrayList;
import java.util.List;

public class Concept7 {

    record Seat(String id) {}

    // Fully immutable: no method changes an existing instance.
    static final class SeatMap {
        private final List<Seat> seats;

        SeatMap(List<Seat> seats) {
            this.seats = List.copyOf(seats);          // copy IN
        }

        List<Seat> seats() {
            return seats;                             // locked snapshot, safe to return
        }

        // The only "mutation": build and return a NEW SeatMap.
        SeatMap withSeatAdded(Seat seat) {
            List<Seat> bigger = new ArrayList<>(seats);
            bigger.add(seat);
            return new SeatMap(bigger);
        }
    }

    public static void main(String[] args) {
        SeatMap v1 = new SeatMap(List.of(new Seat("A1")));

        SeatMap v2 = v1.withSeatAdded(new Seat("A2"));
        SeatMap v3 = v2.withSeatAdded(new Seat("A3"));

        System.out.println("v1: " + v1.seats());
        System.out.println("v2: " + v2.seats());
        System.out.println("v3: " + v3.seats());

        // Classic bug: calling withSeatAdded and ignoring the result.
        v1.withSeatAdded(new Seat("B9"));
        System.out.println("v1 after ignored call: " + v1.seats());
    }
}
