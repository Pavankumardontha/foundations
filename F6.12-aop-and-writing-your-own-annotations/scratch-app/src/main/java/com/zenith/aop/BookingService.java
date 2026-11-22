package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

// A bean with marked methods. Note: the methods contain ONLY their real work.
// The nine duplicated timing lines from lesson 1 are gone (subtask S3's deletion).
@Component
public class BookingService {

    @Timed("checkout")
    public void reserveSeat() throws InterruptedException {
        Thread.sleep(120);
        System.out.println("Seat reserved.");
    }

    @Timed
    public void cancelBooking() throws InterruptedException {
        Thread.sleep(80);
        System.out.println("Booking cancelled.");
    }

    @Timed
    public void listBookings() throws InterruptedException {
        Thread.sleep(40);
        System.out.println("Bookings: [A1, B4, C2]");
    }
}
