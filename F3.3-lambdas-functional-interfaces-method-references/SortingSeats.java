// Concept 7: Lambdas in the wild - sorting with Comparator, three styles side by side.

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SortingSeats {

    record Seat(int row, String section) { }

    public static void main(String[] args) {
        // Note: List.of(...) is immutable, so we copy into an ArrayList we can sort.
        List<Seat> seats = new ArrayList<>(List.of(
                new Seat(8, "Balcony"),
                new Seat(2, "Stalls"),
                new Seat(5, "Stalls")));

        // ---------- Style 1: anonymous class (pre-2014 Java) ----------
        seats.sort(new Comparator<Seat>() {
            public int compare(Seat a, Seat b) {
                return Integer.compare(a.row(), b.row());
            }
        });
        System.out.println("anonymous class : " + seats);

        // ---------- Style 2: lambda ----------
        seats.sort((a, b) -> Integer.compare(a.row(), b.row()));
        System.out.println("lambda          : " + seats);

        // ---------- Style 3: Comparator.comparing + method reference ----------
        // comparing() takes a Function<Seat, X> ("what to sort by") and builds
        // the whole Comparator for you.
        seats.sort(Comparator.comparing(Seat::row));
        System.out.println("comparing       : " + seats);

        // Comparators compose readably: sort by section, then by row within a section.
        seats.sort(Comparator.comparing(Seat::section)
                             .thenComparing(Seat::row));
        System.out.println("section, then row: " + seats);

        // And reverse easily:
        seats.sort(Comparator.comparing(Seat::row).reversed());
        System.out.println("row descending  : " + seats);

        // forEach takes a Consumer<Seat> - both of these work:
        seats.forEach(seat -> System.out.println("  seat: " + seat));
        seats.forEach(System.out::println);
    }
}
