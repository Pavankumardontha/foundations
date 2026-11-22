import java.util.List;

public class Concept5OldBug {

    sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}

    record Granted(String holdId, List<String> seatIds) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}
    record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

    // The old world's trap: the test checks one type, the cast names another.
    // This COMPILES — the compiler cannot connect the test to the cast.
    static void logOldBug(HoldResult r) {
        if (r instanceof Granted) {
            Rejected rej = (Rejected) r;             // wrong cast — compiles anyway
            System.out.println(rej.reason());
        }
    }

    public static void main(String[] args) {
        logOldBug(new Granted("H-101", List.of("A1")));
    }
}
