// Lesson 5 — the target state: methods contain ONLY their real work, plus a marker.
// Compare with lesson 1: the nine timing lines are gone.
// No timing prints yet, because the machinery that reads @Timed does not exist yet.
public class BookingService {

    @Timed
    void reserveSeat() throws InterruptedException {
        Thread.sleep(120);
        System.out.println("Seat reserved.");
    }

    @Timed
    void cancelBooking() throws InterruptedException {
        Thread.sleep(80);
        System.out.println("Booking cancelled.");
    }

    @Timed
    void listBookings() throws InterruptedException {
        Thread.sleep(40);
        System.out.println("Bookings: [A1, B4, C2]");
    }

    public static void main(String[] args) throws InterruptedException {
        BookingService service = new BookingService();
        service.reserveSeat();
        service.cancelBooking();
        service.listBookings();
    }
}
