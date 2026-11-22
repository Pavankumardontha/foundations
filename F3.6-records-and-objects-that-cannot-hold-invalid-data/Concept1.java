public class Concept1 {

    // The OLD way: a plain class holding two pieces of data.
    // We wrote no equals, no hashCode, no toString — just like most people forget to.
    static class MoneyClass {
        private final long amountInPaise;
        private final String currency;

        MoneyClass(long amountInPaise, String currency) {
            this.amountInPaise = amountInPaise;
            this.currency = currency;
        }

        long amountInPaise() { return amountInPaise; }
        String currency()    { return currency; }
    }

    // The NEW way: a record. One line. That's the whole declaration.
    record Money(long amountInPaise, String currency) {}

    public static void main(String[] args) {
        // --- the plain class ---
        MoneyClass c1 = new MoneyClass(450000, "INR");
        MoneyClass c2 = new MoneyClass(450000, "INR");
        System.out.println("class printed:  " + c1);
        System.out.println("class equals:   " + c1.equals(c2));

        // --- the record ---
        Money r1 = new Money(450000, "INR");
        Money r2 = new Money(450000, "INR");
        System.out.println("record printed: " + r1);
        System.out.println("record equals:  " + r1.equals(r2));
        System.out.println("record accessor: " + r1.amountInPaise());
    }
}
