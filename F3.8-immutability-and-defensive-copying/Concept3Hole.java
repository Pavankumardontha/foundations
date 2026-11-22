import java.util.ArrayList;
import java.util.List;

public class Concept3Hole {

    // The Concept 2 class: way OUT fixed, way IN still open.
    static class Order {
        private final List<String> seatIds;

        Order(List<String> seatIds) {
            this.seatIds = seatIds;          // stores the CALLER's list address
        }

        public List<String> seatIds() {
            return List.copyOf(seatIds);
        }
    }

    public static void main(String[] args) {
        List<String> mine = new ArrayList<>(List.of("A1"));
        Order order = new Order(mine);
        System.out.println("Order starts as: " + order.seatIds());

        mine.add("HACKED");                  // I never touched 'order' at all

        System.out.println("Order is now:    " + order.seatIds());
    }
}
