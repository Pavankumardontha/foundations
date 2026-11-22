package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 7: with every relationship LAZY, data loads only when touched.
// Watch the log: three separate selects, each fired by a touch below.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l7-fetch-lazy
@Component
@Profile("l7-fetch-lazy")
class L7FetchLazyDemo implements CommandLineRunner {

    private final SeatRepository seats;

    L7FetchLazyDemo(SeatRepository seats) {
        this.seats = seats;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UUID seatA1 = UUID.fromString("ffffffff-0000-0000-0000-000000000001");

        SeatEntity seat = seats.findById(seatA1).orElseThrow();   // select 1: seat only
        System.out.println(">>> seat loaded: row " + seat.getRowLabel()
                + ", number " + seat.getSeatNumber());

        System.out.println(">>> touching the seat map now ...");
        System.out.println(">>> seat map: " + seat.getSeatMap().getName());        // select 2

        System.out.println(">>> touching the venue now ...");
        System.out.println(">>> venue: " + seat.getSeatMap().getVenue().getName()); // select 3
    }
}
