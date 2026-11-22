import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Concept5Bug {

    // Uses a VIEW where a SNAPSHOT was needed.
    static class Order {
        private final List<String> seatIds = new ArrayList<>();

        void hold(String seat) {
            seatIds.add(seat);
        }

        List<String> seatIds() {
            return Collections.unmodifiableList(seatIds);   // view, not copy
        }
    }

    public static void main(String[] args) {
        Order order = new Order();
        order.hold("A1");

        // Confirmation email captures "the seats at confirmation time"... or so we think.
        List<String> emailSeats = order.seatIds();
        System.out.println("email sent with seats:  " + emailSeats);

        order.hold("B2");   // later, another seat is held on the same order

        System.out.println("email now claims seats: " + emailSeats);  // history rewritten
    }
}
