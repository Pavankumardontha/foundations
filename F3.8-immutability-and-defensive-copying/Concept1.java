import java.util.ArrayList;
import java.util.List;

public class Concept1 {

    // Looks safe: field is private AND final. It is neither.
    static class Order {
        private final List<String> seatIds;

        Order(List<String> seatIds) {
            this.seatIds = seatIds;
        }

        public List<String> seatIds() {
            return seatIds;   // hands out the internal list itself
        }
    }

    public static void main(String[] args) {
        Order order = new Order(new ArrayList<>(List.of("A1", "A2")));
        System.out.println("Order starts as: " + order.seatIds());

        // The "attack": a perfectly innocent-looking caller
        List<String> seats = order.seatIds();
        seats.add("Z99");

        System.out.println("Order is now:    " + order.seatIds());
    }
}
