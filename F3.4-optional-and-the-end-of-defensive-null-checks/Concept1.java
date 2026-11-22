// Concept 1: the problem — a return type that lies.
// findById says it returns an Event. Sometimes it returns null instead.
// The caller has no warning, forgets to check, and crashes far from the cause.

public class Concept1 {

    record Event(String name) {}

    // Looks at the signature: "I return an Event." That is a lie —
    // for an unknown id it returns null, and nothing in the type says so.
    static Event findById(String id) {
        if (id.equals("E1")) {
            return new Event("Coldplay Live");
        }
        return null; // "not found" — invisible to the caller
    }

    public static void main(String[] args) {
        // Happy path — works fine, so the bug hides during testing:
        Event found = findById("E1");
        System.out.println("Found: " + found.name());

        // Unknown id — the caller trusted the signature and did not check:
        Event missing = findById("E999");
        System.out.println("Missing: " + missing.name()); // boom
    }
}
