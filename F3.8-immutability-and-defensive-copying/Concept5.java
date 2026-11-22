import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Concept5 {
    public static void main(String[] args) {
        List<String> original = new ArrayList<>(List.of("A1"));

        List<String> view     = Collections.unmodifiableList(original); // a window
        List<String> snapshot = List.copyOf(original);                  // a photo

        original.add("B2");   // change the ORIGINAL — we never touch view/snapshot

        System.out.println("original: " + original);
        System.out.println("view:     " + view);
        System.out.println("snapshot: " + snapshot);

        // Both refuse direct writes equally:
        try {
            view.add("X");
        } catch (UnsupportedOperationException e) {
            System.out.println("view.add     -> " + e);
        }
        try {
            snapshot.add("X");
        } catch (UnsupportedOperationException e) {
            System.out.println("snapshot.add -> " + e);
        }
    }
}
