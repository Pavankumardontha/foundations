public class Doubt5D {

    sealed interface HoldResult permits Granted, Rejected {}
    record Granted(String holdId) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}

    static String reasonOrDefault(HoldResult r) {
        if (!(r instanceof Rejected rej)) {
            return "not a rejection";               // every non-Rejected leaves here
        }
        // Only way to reach this line: the match SUCCEEDED. So rej exists here.
        return "rejected because: " + rej.reason();
    }

    public static void main(String[] args) {
        System.out.println(reasonOrDefault(new Rejected("sold out")));
        System.out.println(reasonOrDefault(new Granted("H-1")));
    }
}
