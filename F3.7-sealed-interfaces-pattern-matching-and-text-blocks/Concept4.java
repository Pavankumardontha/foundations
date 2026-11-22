import java.util.List;

public class Concept4 {

    sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}

    record Granted(String holdId, List<String> seatIds) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}
    record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

    // Guarded pattern: 'when' attaches an extra condition to a case.
    static String describe(HoldResult r) {
        return switch (r) {
            case Granted(String id, var seats)       -> "granted " + seats + " under hold " + id;
            case Rejected(String reason)             -> "rejected: " + reason;
            case PartiallyGranted p when p.missing() > 5
                                                     -> "ESCALATE: hold " + p.holdId() + " is short "
                                                        + p.missing() + " seats — offer another show";
            case PartiallyGranted p                  -> "partial: hold " + p.holdId() + ", missing "
                                                        + p.missing() + " — offer to continue";
        };
    }

    // The same logic with an if nested inside the branch, for comparison.
    static String describeNestedIf(HoldResult r) {
        return switch (r) {
            case Granted(String id, var seats)       -> "granted " + seats + " under hold " + id;
            case Rejected(String reason)             -> "rejected: " + reason;
            case PartiallyGranted p -> {
                if (p.missing() > 5) {
                    yield "ESCALATE: hold " + p.holdId() + " is short "
                          + p.missing() + " seats — offer another show";
                } else {
                    yield "partial: hold " + p.holdId() + ", missing "
                          + p.missing() + " — offer to continue";
                }
            }
        };
    }

    public static void main(String[] args) {
        List<HoldResult> results = List.of(
                new PartiallyGranted("H-102", List.of("B1"), 2),
                new PartiallyGranted("H-103", List.of("C1"), 8)
        );
        for (HoldResult r : results) {
            System.out.println(describe(r));
        }
        System.out.println("nested-if version agrees: "
                + describeNestedIf(results.get(1)));
    }
}
