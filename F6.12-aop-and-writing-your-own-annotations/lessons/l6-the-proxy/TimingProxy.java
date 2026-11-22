// The stand-in. Same shape as the real object, holds the real object inside,
// does the extra work, forwards the call. This is a hand-made Spring proxy.
public class TimingProxy implements Booking {

    private final Booking real;

    public TimingProxy(Booking real) {
        this.real = real;
    }

    public void reserveSeat() throws InterruptedException {
        long start = System.nanoTime();              // extra work, before

        real.reserveSeat();                          // forward to the real object

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("reserveSeat took " + elapsedMs + " ms");  // extra work, after
    }
}
