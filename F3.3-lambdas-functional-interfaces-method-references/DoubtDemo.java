// Doubt from Concept 2: what does "static DiscountRule noDiscount() { return price -> price; }" do?
// Answer: it is a factory method. It builds and returns an object implementing the interface.

@FunctionalInterface
interface Rule {
    int discountedPrice(int originalPrice);

    static Rule noDiscount() {
        return price -> price; // returns an object implementing Rule
    }

    // the exact same method WITHOUT a lambda, to show they are identical:
    static Rule noDiscountVerbose() {
        return new Rule() {
            public int discountedPrice(int price) {
                return price;
            }
        };
    }
}

public class DoubtDemo {
    public static void main(String[] args) {
        Rule a = Rule.noDiscount();          // step 1: call the factory, get a Rule object
        Rule b = Rule.noDiscountVerbose();

        System.out.println(a.discountedPrice(500));  // step 2: use the object
        System.out.println(b.discountedPrice(500));
    }
}
