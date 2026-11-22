// The real object. Clean: it contains only its real work. No clock code.
public class RealBookingService implements Booking {

    public void reserveSeat() throws InterruptedException {
        Thread.sleep(120);
        System.out.println("Seat reserved.");
    }
}
