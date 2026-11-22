import java.util.ArrayList;
import java.util.List;

public class Concept4 {
    private final List<String> seatIds = new ArrayList<>(List.of("A1"));

    public static void main(String[] args) {
        Concept4 o = new Concept4();

        o.seatIds.add("B2");           // mutating THROUGH a final field
        o.seatIds.add("C3");

        System.out.println("final field, happily mutated: " + o.seatIds);
    }
}
