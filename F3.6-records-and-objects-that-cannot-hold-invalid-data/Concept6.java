import java.util.HashSet;
import java.util.Set;

public class Concept6 {

    // WITHOUT normalisation: stores whatever casing the caller used.
    record MoneyRaw(long amountInPaise, String currency) {
        MoneyRaw {
            if (amountInPaise < 0) {
                throw new IllegalArgumentException("amount must be >= 0, was " + amountInPaise);
            }
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
            }
        }
    }

    // WITH normalisation: validate, then canonicalise before the field is stored.
    record Money(long amountInPaise, String currency) {
        Money {
            if (amountInPaise < 0) {
                throw new IllegalArgumentException("amount must be >= 0, was " + amountInPaise);
            }
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
            }
            currency = currency.toUpperCase();   // reassigns the PARAMETER, not a field
        }

        static Money inr(long paise) { return new Money(paise, "INR"); }
    }

    public static void main(String[] args) {
        // --- without normalisation ---
        MoneyRaw rawUpper = new MoneyRaw(1000, "INR");
        MoneyRaw rawLower = new MoneyRaw(1000, "inr");
        System.out.println("raw upper:  " + rawUpper);
        System.out.println("raw lower:  " + rawLower);
        System.out.println("raw equal?  " + rawUpper.equals(rawLower));
        Set<MoneyRaw> rawSet = new HashSet<>();
        rawSet.add(rawUpper);
        rawSet.add(rawLower);
        System.out.println("raw set size: " + rawSet.size() + "  (same money counted twice!)");

        // --- with normalisation ---
        Money viaFactory = Money.inr(1000);
        Money viaLower   = new Money(1000, "inr");
        System.out.println("factory:    " + viaFactory);
        System.out.println("lowercase:  " + viaLower);
        System.out.println("equal now?  " + viaFactory.equals(viaLower));
        Set<Money> set = new HashSet<>();
        set.add(viaFactory);
        set.add(viaLower);
        System.out.println("set size:   " + set.size());
    }
}
