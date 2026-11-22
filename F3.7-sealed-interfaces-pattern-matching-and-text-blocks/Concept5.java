import java.util.List;

public class Concept5 {

    sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}

    record Granted(String holdId, List<String> seatIds) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}
    record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

    // OLD WORLD: test, then cast — two steps, repeated type name, cast can be wrong.
    static void logIfRejectedOld(HoldResult r) {
        if (r instanceof Rejected) {
            Rejected rej = (Rejected) r;             // the manual cast
            System.out.println("log(old): " + rej.reason());
        }
    }

    // NEW WORLD: pattern matching for instanceof — test, cast, and name in one step.
    static void logIfRejected(HoldResult r) {
        if (r instanceof Rejected rej) {
            System.out.println("log(new): " + rej.reason());
        }
    }

    // Flow scoping with early return: after the if, the compiler KNOWS r is a Rejected,
    // so rej stays usable in the rest of the method.
    static String reasonOrDefault(HoldResult r) {
        if (!(r instanceof Rejected rej)) {
            return "not a rejection";
        }
        return "rejected because: " + rej.reason();  // rej is in scope here
    }

    public static void main(String[] args) {
        HoldResult rejected = new Rejected("sold out");
        HoldResult granted  = new Granted("H-101", List.of("A1"));

        logIfRejectedOld(rejected);
        logIfRejected(rejected);
        logIfRejected(granted);                      // prints nothing — not a Rejected

        System.out.println(reasonOrDefault(rejected));
        System.out.println(reasonOrDefault(granted));
    }
}
