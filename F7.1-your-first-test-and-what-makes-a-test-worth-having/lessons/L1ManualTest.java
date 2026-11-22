// Lesson 1 — a test written by hand, with no libraries.
// Run with:  java L1ManualTest.java
class L1ManualTest {
    public static void main(String[] args) {
        Money ten = new Money(1000, "INR");
        Money five = new Money(500, "INR");

        Money total = ten.plus(five);

        if (total.amountInPaise() != 1500) {
            throw new AssertionError("plus is wrong: expected 1500 but got " + total.amountInPaise());
        }
        System.out.println("OK: plus adds two amounts in the same currency");
    }
}

// Your Money record from F3.6, trimmed to only what this lesson needs.
record Money(long amountInPaise, String currency) {
    Money plus(Money other) {
        return new Money(this.amountInPaise + other.amountInPaise, this.currency);
    }
}
