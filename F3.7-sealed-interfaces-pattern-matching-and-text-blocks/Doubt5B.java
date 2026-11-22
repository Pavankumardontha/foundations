public class Doubt5B {

    sealed interface HoldResult permits Granted, Rejected {}
    record Granted(String holdId) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}

    public static void main(String[] args) {
        HoldResult a = new Rejected("sold out");

        // THE OLD DANCE, three steps:
        if (a instanceof Rejected) {            // step 1: ask
            Rejected rej = (Rejected) a;        // step 2: cast (relabel for the compiler)
            System.out.println(rej.reason());   // step 3: use
        }
    }
}
