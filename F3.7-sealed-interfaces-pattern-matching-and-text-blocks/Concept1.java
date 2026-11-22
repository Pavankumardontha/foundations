import java.util.List;

// The closed set: HoldResult can ONLY be one of these three types. Nothing else, ever.
sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}

record Granted(String holdId, List<String> seatIds) implements HoldResult {}
record Rejected(String reason) implements HoldResult {}
record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

public class Concept1 {
    public static void main(String[] args) {
        HoldResult a = new Granted("H-101", List.of("A1", "A2"));
        HoldResult b = new Rejected("sold out");
        HoldResult c = new PartiallyGranted("H-102", List.of("B1"), 2);

        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
    }
}
