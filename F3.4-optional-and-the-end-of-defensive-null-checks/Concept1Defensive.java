// Concept 1, part 2: the old "fix" — defensive null checks everywhere.
// It works, but every single caller must remember to do this, forever.

public class Concept1Defensive {

    record Event(String name) {}

    static Event findById(String id) {
        if (id.equals("E1")) {
            return new Event("Coldplay Live");
        }
        return null;
    }

    public static void main(String[] args) {
        Event e = findById("E999");
        if (e != null) {                      // the defensive check
            System.out.println(e.name());
        } else {
            System.out.println("unknown");    // fallback
        }
    }
}
