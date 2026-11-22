// Concept 6: lambdas may READ local variables, but only if they are "effectively final"
// (never reassigned). This file shows what works and the correct way to accumulate.

import java.util.List;

public class EffectivelyFinal {
    public static void main(String[] args) {
        List<String> names = List.of("Asha", "Ravi", "Meena");

        // READING a local variable inside a lambda is fine,
        // as long as that variable is never reassigned:
        String greeting = "Hello, ";                          // assigned once -> effectively final
        names.forEach(name -> System.out.println(greeting + name));

        // The CORRECT way to count: don't capture-and-mutate, use the right tool.
        // Here, simply ask the collection:
        long count = names.size();
        System.out.println("count via size()   = " + count);

        // Or, previewing streams (Task F3.5): count the ones matching a condition:
        long shortNames = names.stream().filter(n -> n.length() <= 4).count();
        System.out.println("names with <= 4 letters = " + shortNames);
    }
}
