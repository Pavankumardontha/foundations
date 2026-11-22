// Lesson 1 — the problem: the same timing block copied into every method.
// Plain Java, no Spring. Compile: javac BookingService.java   Run: java BookingService
public class BookingService {

    void reserveSeat() throws InterruptedException {
        long start = System.nanoTime();                              // timing code

        Thread.sleep(120);                                           // the real work (pretend)
        System.out.println("Seat reserved.");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;    // timing code
        System.out.println("reserveSeat took " + elapsedMs + " ms"); // timing code
    }

    void cancelBooking() throws InterruptedException {
        long start = System.nanoTime();                              // timing code — copy 2

        Thread.sleep(80);                                            // the real work (pretend)
        System.out.println("Booking cancelled.");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;    // timing code — copy 2
        System.out.println("cancelBooking took " + elapsedMs + " ms"); // timing code — copy 2
    }

    void listBookings() throws InterruptedException {
        long start = System.nanoTime();                              // timing code — copy 3

        Thread.sleep(40);                                            // the real work (pretend)
        System.out.println("Bookings: [A1, B4, C2]");

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;    // timing code — copy 3
        System.out.println("listBookings took " + elapsedMs + " ms"); // timing code — copy 3
    }

    public static void main(String[] args) throws InterruptedException {
        BookingService service = new BookingService();
        service.reserveSeat();
        service.cancelBooking();
        service.listBookings();
    }
}
