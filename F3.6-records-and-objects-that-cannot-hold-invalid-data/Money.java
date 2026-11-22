/**
 * Capstone for F3.6 — everything the task built, in one record.
 * Run `java Money` to see every guarantee proven.
 */
public record Money(long amountInPaise, String currency) {

    public Money {                                   // compact constructor
        if (amountInPaise < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amountInPaise);
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
        }
        currency = currency.toUpperCase();           // normalise before storage
    }

    public static Money inr(long paise) {            // static factory
        return new Money(paise, "INR");
    }

    public Money plus(Money other) {                 // behaviour with its own rule
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: cannot add " + other.currency + " to " + this.currency);
        }
        return new Money(this.amountInPaise + other.amountInPaise, this.currency);
    }

    // ---- proof that an invalid Money cannot exist ----
    public static void main(String[] args) {
        System.out.println("valid:        " + Money.inr(450000));
        System.out.println("normalised:   " + Money.inr(1000).equals(new Money(1000, "inr")));
        System.out.println("plus:         " + Money.inr(450000).plus(Money.inr(25000)));

        expectRejection("negative amount",  () -> new Money(-1, "INR"));
        expectRejection("null currency",    () -> new Money(100, null));
        expectRejection("bad currency",     () -> new Money(100, "RUPEES"));
        expectRejection("currency mismatch", () -> Money.inr(100).plus(new Money(100, "USD")));

        System.out.println("all rejections proven — an invalid Money cannot exist");
    }

    private static void expectRejection(String label, Runnable attempt) {
        try {
            attempt.run();
            throw new AssertionError("BUG: " + label + " was NOT rejected");
        } catch (IllegalArgumentException e) {
            System.out.println("rejected " + label + ": " + e.getMessage());
        }
    }
}
