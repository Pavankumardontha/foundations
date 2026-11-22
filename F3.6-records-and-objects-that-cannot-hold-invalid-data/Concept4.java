public class Concept4 {

    record Money(long amountInPaise, String currency) {
        Money {
            if (amountInPaise < 0) {
                throw new IllegalArgumentException("amount must be >= 0, was " + amountInPaise);
            }
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
            }
        }

        // Behaviour: a record can carry the rules about its own data.
        Money plus(Money other) {
            if (!this.currency.equals(other.currency)) {
                throw new IllegalArgumentException(
                        "currency mismatch: cannot add " + other.currency + " to " + this.currency);
            }
            return new Money(this.amountInPaise + other.amountInPaise, this.currency);
        }
    }

    public static void main(String[] args) {
        Money ticket = new Money(450000, "INR");
        Money fee    = new Money(25000, "INR");

        Money total = ticket.plus(fee);
        System.out.println("ticket:  " + ticket);
        System.out.println("fee:     " + fee);
        System.out.println("total:   " + total);
        System.out.println("ticket unchanged? " + (ticket.amountInPaise() == 450000));

        // Mismatched currencies must be rejected, not silently added.
        Money usd = new Money(1000, "USD");
        System.out.println("now adding USD to INR...");
        Money nonsense = ticket.plus(usd);
        System.out.println("this line never runs: " + nonsense);
    }
}
