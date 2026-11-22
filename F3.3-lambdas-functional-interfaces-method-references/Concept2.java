// Concept 2: What exactly is a "functional interface".
// Rule: exactly ONE abstract method. default/static methods do not count.

@FunctionalInterface
interface DiscountRule {
    int discountedPrice(int originalPrice); // the ONE abstract method

    // default methods are allowed - they have a body, so they don't count
    default String describe() {
        return "A rule that discounts a price";
    }

    // static methods are allowed too - they also have a body
    static DiscountRule noDiscount() {
        return price -> price;
    }
}

public class Concept2 {
    public static void main(String[] args) {
        // Because DiscountRule has exactly one abstract method,
        // a lambda can implement it:
        DiscountRule halfPrice = price -> price / 2;
        DiscountRule tenOff    = price -> price - 10;

        System.out.println("half of 500  = " + halfPrice.discountedPrice(500));
        System.out.println("ten off 500  = " + tenOff.discountedPrice(500));

        // The default and static methods still work as normal:
        System.out.println(halfPrice.describe());
        System.out.println("no discount  = " + DiscountRule.noDiscount().discountedPrice(500));
    }
}
