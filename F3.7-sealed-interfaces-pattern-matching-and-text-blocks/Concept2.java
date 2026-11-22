import java.util.List;

public class Concept2 {

    sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}

    record Granted(String holdId, List<String> seatIds) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}
    record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

    // A switch over the sealed type with NO default branch.
    // It compiles because the compiler can see every possibility.
    static String describe(HoldResult r) {
        return switch (r) {
            case Granted g          -> "granted: hold " + g.holdId() + " for seats " + g.seatIds();
            case Rejected rej       -> "rejected: " + rej.reason();
            case PartiallyGranted p -> "partial: hold " + p.holdId() + ", got " + p.seatIds()
                                       + ", still missing " + p.missing();
        };
    }

    public static void main(String[] args) {
        List<HoldResult> results = List.of(
                new Granted("H-101", List.of("A1", "A2")),
                new Rejected("sold out"),
                new PartiallyGranted("H-102", List.of("B1"), 2)
        );
        for (HoldResult r : results) {
            System.out.println(describe(r));
        }
    }
}
