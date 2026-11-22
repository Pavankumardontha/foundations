public class Concept3Nested {

    record Seat(String row, int number) {}
    record Booking(String id, Seat seat) {}

    // A record pattern can nest: destructure the Booking AND the Seat inside it, in one case.
    static String where(Object o) {
        return switch (o) {
            case Booking(String id, Seat(String row, int num)) ->
                    "booking " + id + " is at row " + row + ", seat " + num;
            default -> "not a booking";
        };
    }

    public static void main(String[] args) {
        System.out.println(where(new Booking("B-7", new Seat("C", 12))));
        System.out.println(where("just a string"));
    }
}
