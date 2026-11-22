package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 8: what a LAZY field actually holds - a proxy, Hibernate's stand-in.
// Watch where the seat_map select fires (and where it does not).
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-proxy
@Component
@Profile("l8-proxy")
class L8ProxyDemo implements CommandLineRunner {

    private final SeatRepository seats;

    L8ProxyDemo(SeatRepository seats) {
        this.seats = seats;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UUID seatA1 = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        SeatEntity seat = seats.findById(seatA1).orElseThrow();   // select 1: seat only

        SeatMapEntity map = seat.getSeatMap();    // taking the stand-in: no select

        System.out.println(">>> is it null?                  " + (map == null));
        System.out.println(">>> its class:                   " + map.getClass().getName());
        System.out.println(">>> exactly SeatMapEntity.class? " + (map.getClass() == SeatMapEntity.class));
        System.out.println(">>> instanceof SeatMapEntity?    " + (map instanceof SeatMapEntity));
        System.out.println(">>> its id (watch the log):      " + map.getId());
        System.out.println(">>> now its name (watch the log) ...");
        System.out.println(">>> name: " + map.getName());
        System.out.println(">>> class after loading:         " + map.getClass().getName());
    }
}
