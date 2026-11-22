// Concept 5: Method references - the four shapes.
// A method reference is a lambda whose body does nothing except call ONE existing method.

import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.ArrayList;

public class MethodReferences {

    record Seat(int row, String section) { }

    public static void main(String[] args) {

        // ---------- Shape 1: STATIC method -> ClassName::staticMethod ----------
        Function<String, Integer> parseLambda = text -> Integer.parseInt(text);
        Function<String, Integer> parseRef    = Integer::parseInt;
        System.out.println("parseLambda(\"42\") = " + parseLambda.apply("42"));
        System.out.println("parseRef(\"42\")    = " + parseRef.apply("42"));

        // ---------- Shape 2: BOUND to a particular object -> object::method ----------
        // System.out is one specific object; the reference always prints through IT.
        Consumer<String> printLambda = line -> System.out.println(line);
        Consumer<String> printRef    = System.out::println;
        printLambda.accept("printed via lambda");
        printRef.accept("printed via method reference");

        // ---------- Shape 3: UNBOUND, on an arbitrary instance -> ClassName::instanceMethod ----------
        // No object yet! The object arrives later as the lambda's parameter.
        Function<Seat, Integer> rowLambda = seat -> seat.row();
        Function<Seat, Integer> rowRef    = Seat::row;
        Seat seat = new Seat(7, "Balcony");
        System.out.println("rowLambda(seat) = " + rowLambda.apply(seat));
        System.out.println("rowRef(seat)    = " + rowRef.apply(seat));

        // ---------- Shape 4: CONSTRUCTOR -> ClassName::new ----------
        Supplier<ArrayList<String>> listLambda = () -> new ArrayList<>();
        Supplier<ArrayList<String>> listRef    = ArrayList::new;
        ArrayList<String> names = listRef.get();   // actually constructs the list
        names.add("Asha");
        System.out.println("list built via reference: " + names);

        // ---------- The same example from verbose to terse ----------
        Function<Seat, Integer> v1 = (Seat s) -> { return s.row(); }; // everything spelled out
        Function<Seat, Integer> v2 = s -> s.row();                    // inferred type, expression body
        Function<Seat, Integer> v3 = Seat::row;                       // method reference
        System.out.println("v1=" + v1.apply(seat) + "  v2=" + v2.apply(seat) + "  v3=" + v3.apply(seat));
    }
}
