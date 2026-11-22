// The leak: calling the real object directly. The proxy exists but is never used,
// so the extra work never happens. (This is the seed of the self-invocation bug.)
public class DirectCall {

    public static void main(String[] args) throws InterruptedException {
        RealBookingService real = new RealBookingService();
        Booking service = new TimingProxy(real);   // proxy exists, but...

        real.reserveSeat();                        // ...the call goes straight to the real object
    }
}
