// Concept 4: The four built-in functional interfaces (plus BiFunction).
// Java already ships the one-method interfaces you keep needing, in java.util.function.

import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.BiFunction;

public class BuiltInInterfaces {
    public static void main(String[] args) {

        // ---------- Predicate<T>: takes a T, returns boolean. For yes/no tests. ----------
        Predicate<String> isLong = word -> word.length() > 5;
        System.out.println("isLong(\"hi\")        = " + isLong.test("hi"));
        System.out.println("isLong(\"elephant\")  = " + isLong.test("elephant"));

        // ---------- Function<A, B>: takes an A, returns a B. For converting. ----------
        Function<String, Integer> length = word -> word.length();
        System.out.println("length(\"elephant\")  = " + length.apply("elephant"));

        // ---------- Supplier<T>: takes NOTHING, returns a T. For producing on demand. ----------
        Supplier<Double> randomPercent = () -> Math.random() * 100;
        System.out.println("randomPercent.get() = " + randomPercent.get());
        System.out.println("randomPercent.get() = " + randomPercent.get()); // fresh value each call

        // ---------- Consumer<T>: takes a T, returns NOTHING. For side effects. ----------
        Consumer<String> shout = message -> System.out.println(message.toUpperCase() + "!!!");
        shout.accept("the show is starting");

        // ---------- BiFunction<A, B, C>: takes an A and a B, returns a C. ----------
        BiFunction<String, Integer, String> repeat = (word, times) -> word.repeat(times);
        System.out.println("repeat(\"ha\", 3)     = " + repeat.apply("ha", 3));
    }
}
