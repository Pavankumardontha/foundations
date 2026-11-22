public class Concept2 {

    record Money(long amountInPaise, String currency) {}

    public static void main(String[] args) {
        Money price = new Money(450000, "INR");

        // You cannot change `price`. To get a different amount, you build a NEW Money.
        Money discounted = new Money(price.amountInPaise() - 50000, price.currency());

        System.out.println("original:   " + price);
        System.out.println("discounted: " + discounted);
        System.out.println("original unchanged? " + (price.amountInPaise() == 450000));
    }
}
