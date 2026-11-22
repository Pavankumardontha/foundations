// Lambda basics: what a lambda is and every way to write one.

// Three tiny one-method interfaces to practise with:

interface Greeting {
    void sayHello();                 // takes nothing, returns nothing
}

interface Doubler {
    int twice(int n);                // takes one int, returns an int
}

interface MathOp {
    int calculate(int a, int b);     // takes two ints, returns an int
}

public class LambdaBasics {
    public static void main(String[] args) {

        // ---------- Shape 1: no parameters ----------
        // "take nothing, then print a line"
        Greeting g = () -> System.out.println("Hello!");
        g.sayHello();   // the body runs NOW, not when the lambda was written

        // ---------- Shape 2: one parameter ----------
        // "take n, give back n * 2"
        Doubler d = n -> n * 2;
        System.out.println("twice(21) = " + d.twice(21));

        // ---------- Shape 3: two parameters ----------
        // "take a and b, give back a + b"
        MathOp add      = (a, b) -> a + b;
        MathOp multiply = (a, b) -> a * b;
        System.out.println("add(3, 4)      = " + add.calculate(3, 4));
        System.out.println("multiply(3, 4) = " + multiply.calculate(3, 4));

        // ---------- Shape 4: a body with several statements ----------
        // Use { } and an explicit return when one expression is not enough.
        MathOp max = (a, b) -> {
            System.out.println("  comparing " + a + " and " + b);
            if (a > b) {
                return a;
            }
            return b;
        };
        System.out.println("max(7, 12)     = " + max.calculate(7, 12));

        // ---------- A lambda is a value: it only runs when called ----------
        Greeting later = () -> System.out.println("I run only when called!");
        System.out.println("lambda created, nothing printed yet...");
        later.sayHello();   // <-- the body runs here
        later.sayHello();   // <-- and again here; it can run many times
    }
}
