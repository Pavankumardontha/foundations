// Concept 1: Passing BEHAVIOUR as a value, using a one-method interface.
// Three ways to implement the same interface: named class, anonymous class, lambda.

interface SeatFilter {
    boolean test(int rowNumber); // exactly ONE abstract method
}

// Way 1: a named class, written out in full
class FrontRowFilter implements SeatFilter {
    public boolean test(int rowNumber) {
        return rowNumber <= 3;
    }
}

public class Concept1 {

    // This method does not care HOW the filter decides. It just calls test().
    static void printMatchingRows(int[] rows, SeatFilter filter) {
        for (int row : rows) {
            if (filter.test(row)) {
                System.out.println("Row " + row + " matches");
            }
        }
    }

    public static void main(String[] args) {
        int[] rows = {1, 2, 5, 8, 3};

        System.out.println("--- Way 1: named class ---");
        printMatchingRows(rows, new FrontRowFilter());

        System.out.println("--- Way 2: anonymous class ---");
        printMatchingRows(rows, new SeatFilter() {
            public boolean test(int rowNumber) {
                return rowNumber <= 3;
            }
        });

        System.out.println("--- Way 3: lambda ---");
        printMatchingRows(rows, rowNumber -> rowNumber <= 3);
    }
}
