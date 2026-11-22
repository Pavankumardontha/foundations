public class Doubt5A {

    sealed interface HoldResult permits Granted, Rejected {}
    record Granted(String holdId) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}

    public static void main(String[] args) {
        HoldResult a = new Rejected("sold out");
        HoldResult b = new Granted("H-1");

        // instanceof is a yes/no question about what the object REALLY is.
        System.out.println("is a a Rejected? " + (a instanceof Rejected));
        System.out.println("is b a Rejected? " + (b instanceof Rejected));
    }
}
