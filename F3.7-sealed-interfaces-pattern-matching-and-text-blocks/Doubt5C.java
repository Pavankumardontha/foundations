public class Doubt5C {

    sealed interface HoldResult permits Granted, Rejected {}
    record Granted(String holdId) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}

    public static void main(String[] args) {
        HoldResult a = new Rejected("sold out");
        HoldResult b = new Granted("H-1");

        // THE NEW FORM: ask + relabel + name, all in one.
        if (a instanceof Rejected rej) {
            System.out.println("a: " + rej.reason());
        }

        if (b instanceof Rejected rej) {
            System.out.println("b: " + rej.reason());   // never runs: b is a Granted
        }
        System.out.println("done");
    }
}
