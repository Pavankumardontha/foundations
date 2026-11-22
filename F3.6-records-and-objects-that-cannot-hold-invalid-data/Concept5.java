public class Concept5 {

    record Money(long amountInPaise, String currency) {
        Money {
            if (amountInPaise < 0) {
                throw new IllegalArgumentException("amount must be >= 0, was " + amountInPaise);
            }
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
            }
        }

        // Static factories: named creation methods.
        static Money inr(long paise)  { return new Money(paise, "INR"); }
        static Money usd(long cents)  { return new Money(cents, "USD"); }
    }

    public static void main(String[] args) {
        // The same value, created two ways.
        Money viaConstructor = new Money(450000, "INR");
        Money viaFactory     = Money.inr(450000);

        System.out.println("constructor: " + viaConstructor);
        System.out.println("factory:     " + viaFactory);
        System.out.println("equal?       " + viaConstructor.equals(viaFactory));

        // The factory is NOT a back door: it calls the same constructor.
        try {
            Money.inr(-100);
        } catch (IllegalArgumentException e) {
            System.out.println("factory rejected: " + e.getMessage());
        }
    }
}
