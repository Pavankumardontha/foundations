package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 8 and 9 demo. Run with profile l8-timed (lesson 8) or l9-value (lesson 9).
@Component
@Profile({"l8-timed", "l9-value"})
public class L8TimedDemo implements CommandLineRunner {

    private final BookingService bookingService;

    public L8TimedDemo(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Override
    public void run(String... args) throws Exception {
        bookingService.reserveSeat();
        bookingService.cancelBooking();
        bookingService.listBookings();
    }
}
