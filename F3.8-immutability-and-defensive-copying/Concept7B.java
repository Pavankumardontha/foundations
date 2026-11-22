public class Concept7B {

    static final class Counter {
        private final int value;

        Counter(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }

        Counter plusOne() {
            return new Counter(value + 1);   // NEW counter; this one is untouched
        }
    }

    public static void main(String[] args) {
        Counter c1 = new Counter(0);
        Counter c2 = c1.plusOne();
        Counter c3 = c2.plusOne();

        System.out.println("c1 = " + c1.value());
        System.out.println("c2 = " + c2.value());
        System.out.println("c3 = " + c3.value());
    }
}
