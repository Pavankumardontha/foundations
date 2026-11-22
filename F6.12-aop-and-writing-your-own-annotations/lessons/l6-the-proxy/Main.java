// The caller receives the stand-in and cannot tell the difference.
public class Main {

    public static void main(String[] args) throws InterruptedException {
        Booking service = new TimingProxy(new RealBookingService());
        service.reserveSeat();   // the caller thinks it talks to a booking service
    }
}
