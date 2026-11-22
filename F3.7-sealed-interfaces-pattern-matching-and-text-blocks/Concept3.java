import java.util.List;

public class Concept3 {

    sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}

    record Granted(String holdId, List<String> seatIds) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}
    record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

    // BEFORE — Concept 2 style: type pattern, then accessor calls on g / rej / p.
    static String describeOld(HoldResult r) {
        return switch (r) {
            case Granted g          -> "granted " + g.seatIds() + " under hold " + g.holdId();
            case Rejected rej       -> "rejected: " + rej.reason();
            case PartiallyGranted p -> "got " + p.seatIds() + ", missing " + p.missing();
        };
    }

    // AFTER — record patterns: components pulled straight into variables.
    static String describe(HoldResult r) {
        return switch (r) {
            case Granted(String id, var seats)                     -> "granted " + seats + " under hold " + id;
            case Rejected(String reason)                           -> "rejected: " + reason;
            case PartiallyGranted(var id, var seats, int missing)  -> "got " + seats + ", missing " + missing
                                                                      + " (hold " + id + ")";
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
