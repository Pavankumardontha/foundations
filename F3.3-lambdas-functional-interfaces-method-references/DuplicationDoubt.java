// Doubt: if TWO methods need the front-row logic, does an anonymous class
// force me to write the logic twice?

interface SeatFilter2 {
    boolean test(int rowNumber);
}

public class DuplicationDoubt {

    // Method 1: prints matching rows
    static void printMatchingRows(int[] rows, SeatFilter2 filter) {
        for (int row : rows) {
            if (filter.test(row)) {
                System.out.println("Row " + row + " matches");
            }
        }
    }

    // Method 2: counts matching rows
    static int countMatchingRows(int[] rows, SeatFilter2 filter) {
        int count = 0;
        for (int row : rows) {
            if (filter.test(row)) {
                count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        int[] rows = {1, 2, 3, 4, 5, 6};

        // --- The duplication problem: logic written TWICE ---
        System.out.println("-- inline anonymous classes (logic duplicated) --");
        printMatchingRows(rows, new SeatFilter2() {
            public boolean test(int rowNumber) {
                return rowNumber <= 3;          // copy 1 of the logic
            }
        });
        int c1 = countMatchingRows(rows, new SeatFilter2() {
            public boolean test(int rowNumber) {
                return rowNumber <= 3;          // copy 2 of the SAME logic
            }
        });
        System.out.println("count = " + c1);

        // --- The fix: write it ONCE, store it in a variable, reuse ---
        System.out.println("-- anonymous class stored in a variable (logic once) --");
        SeatFilter2 frontRow = new SeatFilter2() {
            public boolean test(int rowNumber) {
                return rowNumber <= 3;          // the ONLY copy
            }
        };
        printMatchingRows(rows, frontRow);
        int c2 = countMatchingRows(rows, frontRow);
        System.out.println("count = " + c2);
    }
}
