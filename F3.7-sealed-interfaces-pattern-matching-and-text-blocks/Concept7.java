import java.util.List;

public class Concept7 {

    // CHOICE 1 — enum: a fixed set of VALUES.
    // REJECTED is ONE object, shared by the whole program. It cannot carry
    // "sold out" for this request and "payment failed" for that request.
    enum HoldStatus { GRANTED, REJECTED, PARTIAL }

    // CHOICE 2 — sealed interface: a fixed set of TYPES.
    // Every request gets its OWN Rejected object with its OWN reason.
    sealed interface HoldResult permits Granted, Rejected, PartiallyGranted {}
    record Granted(String holdId, List<String> seatIds) implements HoldResult {}
    record Rejected(String reason) implements HoldResult {}
    record PartiallyGranted(String holdId, List<String> seatIds, int missing) implements HoldResult {}

    // CHOICE 3 — open interface: anyone may add implementations, forever.
    // Right when the set must grow freely (like discount rules added every season).
    interface DiscountRule {
        double apply(double price);
    }

    public static void main(String[] args) {
        // enum values are singletons — always the same object:
        HoldStatus s1 = HoldStatus.REJECTED;
        HoldStatus s2 = HoldStatus.REJECTED;
        System.out.println("same enum object? " + (s1 == s2));

        // sealed-type instances each carry their own data:
        Rejected r1 = new Rejected("sold out");
        Rejected r2 = new Rejected("payment failed");
        System.out.println("same Rejected object? " + (r1 == r2));
        System.out.println("r1 says: " + r1.reason());
        System.out.println("r2 says: " + r2.reason());

        // an enum CAN say "which outcome" — it just cannot say anything more:
        HoldStatus status = HoldStatus.REJECTED;
        System.out.println("status = " + status + " ... but WHY? The enum cannot tell us.");
    }
}
