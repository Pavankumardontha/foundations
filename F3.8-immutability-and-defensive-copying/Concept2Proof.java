import java.util.ArrayList;
import java.util.List;

public class Concept2Proof {

    static class Order {
        private final List<String> seatIds;

        Order(List<String> seatIds) {
            this.seatIds = seatIds;
        }

        public List<String> seatIds() {
            return List.copyOf(seatIds);
        }
    }

    public static void main(String[] args) {
        Order order = new Order(new ArrayList<>(List.of("A1", "A2")));

        try {
            order.seatIds().add("Z99");
        } catch (UnsupportedOperationException e) {
            System.out.println("Attack failed with: " + e);
        }

        System.out.println("Order is still:    " + order.seatIds());
    }
}
