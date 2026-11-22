import java.util.ArrayList;
import java.util.List;

public class Concept3 {

    // Defended in BOTH directions.
    static class Order {
        private final List<String> seatIds;

        Order(List<String> seatIds) {
            this.seatIds = List.copyOf(seatIds);   // copy on the way IN
        }

        public List<String> seatIds() {
            return seatIds;                        // already an unmodifiable snapshot
        }
    }

    public static void main(String[] args) {
        // Attack 1: the way in — keep a reference to what we passed, edit it later.
        List<String> mine = new ArrayList<>(List.of("A1"));
        Order order = new Order(mine);

        mine.add("HACKED");
        System.out.println("mine is:  " + mine);
        System.out.println("order is: " + order.seatIds());

        // Attack 2: the way out — mutate what the getter returns.
        try {
            order.seatIds().add("Z99");
        } catch (UnsupportedOperationException e) {
            System.out.println("way-out attack failed with: " + e);
        }
        System.out.println("order is: " + order.seatIds());
    }
}
