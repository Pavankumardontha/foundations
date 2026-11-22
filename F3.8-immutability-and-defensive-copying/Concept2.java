import java.util.ArrayList;
import java.util.List;

public class Concept2 {

    static class Order {
        private final List<String> seatIds;

        Order(List<String> seatIds) {
            this.seatIds = seatIds;          // way IN still open — Concept 3
        }

        public List<String> seatIds() {
            return List.copyOf(seatIds);     // copy on the way OUT
        }
    }

    public static void main(String[] args) {
        Order order = new Order(new ArrayList<>(List.of("A1", "A2")));
        System.out.println("Order starts as: " + order.seatIds());

        List<String> seats = order.seatIds();
        seats.add("Z99");                    // exact same attack as Concept 1

        System.out.println("Order is now:    " + order.seatIds());
    }
}
