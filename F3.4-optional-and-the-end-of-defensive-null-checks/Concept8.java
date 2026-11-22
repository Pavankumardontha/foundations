// Concept 8: where Optional must NEVER appear — and what goes wrong if it does.

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Concept8 {

    // RULE BREAK 1: Optional as a FIELD.
    static class UserProfile {
        Optional<String> nickname; // looks safe... but fields start as null!
    }

    // RULE BREAK 2: Optional as a METHOD PARAMETER.
    static void greet(Optional<String> name) {
        System.out.println("Hello, " + name.orElse("guest"));
    }

    public static void main(String[] args) {
        // --- Parameter problem A: every caller must wrap. Clumsy. ---
        greet(Optional.of("Pavan"));      // caller forced to box the value
        greet(Optional.empty());          // caller forced to box the nothing

        // --- Parameter problem B: the box itself can be null! ---
        try {
            greet(null);                  // compiles fine...
        } catch (NullPointerException e) {
            System.out.println("greet(null) crashed: " + e.getMessage());
        }

        // --- RULE BREAK 3: Optional inside a collection. Two kinds of nothing. ---
        Map<String, Optional<String>> seatHolder = new HashMap<>();
        seatHolder.put("A1", Optional.of("Pavan"));
        seatHolder.put("A2", Optional.empty());   // seat exists, nobody in it
        System.out.println("A1: " + seatHolder.get("A1"));
        System.out.println("A2: " + seatHolder.get("A2")); // Optional.empty
        System.out.println("A3: " + seatHolder.get("A3")); // null! not Optional.empty

        // --- Field problem: the field starts as null, box or no box. ---
        UserProfile p = new UserProfile();
        System.out.println(p.nickname.orElse("no nickname")); // crash
    }
}
