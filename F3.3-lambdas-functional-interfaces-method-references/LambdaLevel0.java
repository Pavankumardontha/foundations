// Lambdas from zero: derivation from anonymous class, and assigning to variables.

interface SeatFilter3 {
    boolean test(int rowNumber);     // exactly ONE abstract method
}

public class LambdaLevel0 {

    static void printMatchingRows(int[] rows, SeatFilter3 filter) {
        for (int row : rows) {
            if (filter.test(row)) {
                System.out.println("Row " + row + " matches");
            }
        }
    }

    public static void main(String[] args) {
        int[] rows = {1, 2, 3, 4, 5, 6};

        // Step 1: the anonymous class, stored in a variable (already familiar)
        SeatFilter3 viaAnonymous = new SeatFilter3() {
            public boolean test(int rowNumber) {
                return rowNumber <= 3;
            }
        };
        System.out.println("viaAnonymous.test(2) = " + viaAnonymous.test(2));
        System.out.println("viaAnonymous.test(5) = " + viaAnonymous.test(5));

        // Step 2: the lambda, stored in a variable the SAME way
        SeatFilter3 viaLambda = rowNumber -> rowNumber <= 3;
        System.out.println("viaLambda.test(2)    = " + viaLambda.test(2));
        System.out.println("viaLambda.test(5)    = " + viaLambda.test(5));

        // Step 3: the variable can be passed to any method expecting a SeatFilter3
        System.out.println("-- passing the lambda variable --");
        printMatchingRows(rows, viaLambda);

        // Step 4: or skip the variable and write the lambda directly in the call
        System.out.println("-- passing a lambda directly --");
        printMatchingRows(rows, rowNumber -> rowNumber <= 3);
    }
}
