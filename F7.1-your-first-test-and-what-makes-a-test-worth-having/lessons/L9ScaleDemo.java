// Lesson 9 — BigDecimal equality: equals() compares value AND scale.
// Run with:  java L9ScaleDemo.java
import java.math.BigDecimal;

class L9ScaleDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("15.0");
        BigDecimal b = new BigDecimal("15.00");

        System.out.println("a.equals(b)    = " + a.equals(b));
        System.out.println("a.compareTo(b) = " + a.compareTo(b));
        System.out.println("a.scale()      = " + a.scale());
        System.out.println("b.scale()      = " + b.scale());

        BigDecimal sum = new BigDecimal("10.5").add(new BigDecimal("4.5"));
        System.out.println("10.5 + 4.5     = " + sum + "   (scale " + sum.scale() + ")");
    }
}
