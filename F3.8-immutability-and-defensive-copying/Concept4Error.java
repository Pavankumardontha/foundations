import java.util.ArrayList;
import java.util.List;

public class Concept4Error {
    private final List<String> seatIds = new ArrayList<>(List.of("A1"));

    void swap() {
        seatIds = new ArrayList<>();   // re-pointing a final field
    }
}
