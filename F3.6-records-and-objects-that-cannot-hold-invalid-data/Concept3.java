public class Concept3 {

    record Money(long amountInPaise, String currency) {
        Money {    // compact constructor: no parameter list, runs BEFORE fields are assigned
            if (amountInPaise < 0) {
                throw new IllegalArgumentException("amount must be >= 0, was " + amountInPaise);
            }
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
            }
        }
    }

    public static void main(String[] args) {
        // A valid Money sails through the checks.
        Money price = new Money(450000, "INR");
        System.out.println("valid:    " + price);

        // Both invalid attempts, caught here ONLY so one run can show both rejections.
        try {
            new Money(-1, "INR");
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }
        try {
            new Money(1000, "RUPEES");
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        // And once with no safety net, to see the real crash.
        System.out.println("now building a negative Money with no try/catch...");
        Money impossible = new Money(-500, "INR");
        System.out.println("this line never runs: " + impossible);
    }
}
