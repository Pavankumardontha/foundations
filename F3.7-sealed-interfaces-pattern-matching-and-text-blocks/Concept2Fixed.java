import java.util.List;

public class Concept2Fixed {

    sealed interface HoldResult permits Granted, Rejected, PartiallyGranted, Expired {}

    record Granted(String holdId, List<String> seatIds) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}
    record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}
    record Expired(String holdId) implements HoldResult {}

    static String describe(HoldResult r) {
        return switch (r) {
            case Granted g          -> "granted: hold " + g.holdId() + " for seats " + g.seatIds();
            case Rejected rej       -> "rejected: " + rej.reason();
            case PartiallyGranted p -> "partial: hold " + p.holdId() + ", got " + p.seatIds()
                                       + ", still missing " + p.missing();
            case Expired e          -> "expired: hold " + e.holdId() + " no longer reserved";
        };
    }

    public static void main(String[] args) {
        List<HoldResult> results = List.of(
                new Granted("H-101", List.of("A1", "A2")),
                new Rejected("sold out"),
                new PartiallyGranted("H-102", List.of("B1"), 2),
                new Expired("H-103")
        );
        for (HoldResult r : results) {
            System.out.println(describe(r));
        }
    }
}
