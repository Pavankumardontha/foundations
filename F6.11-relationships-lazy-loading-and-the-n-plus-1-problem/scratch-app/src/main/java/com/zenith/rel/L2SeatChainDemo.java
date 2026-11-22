package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 2: load ONE seat, then walk the chain: seat -> seat map -> venue.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l2-seat-chain
@Component
@Profile("l2-seat-chain")
class L2SeatChainDemo implements CommandLineRunner {

    private final SeatRepository seats;

    L2SeatChainDemo(SeatRepository seats) {
        this.seats = seats;
    }

    @Override
    public void run(String... args) {
        UUID seatA1 = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        SeatEntity seat = seats.findById(seatA1).orElseThrow();

        System.out.println(">>> seat:     row " + seat.getRowLabel()
                + ", number " + seat.getSeatNumber());
        System.out.println(">>> seat map: " + seat.getSeatMap().getName());
        System.out.println(">>> venue:    " + seat.getSeatMap().getVenue().getName()
                + " (" + seat.getSeatMap().getVenue().getCity() + ")");
    }
}
