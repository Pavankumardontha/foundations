package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 3: load ONE seat map, then read its seats from the mapped list.
// @Transactional keeps one transaction open for the whole method, so Hibernate
// may still load the list when we first touch it (Lessons 7-9 explain this fully).
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l3-seats-list
@Component
@Profile("l3-seats-list")
class L3SeatsListDemo implements CommandLineRunner {

    private final SeatMapRepository seatMaps;

    L3SeatsListDemo(SeatMapRepository seatMaps) {
        this.seatMaps = seatMaps;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UUID riversideFloor = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
        SeatMapEntity map = seatMaps.findById(riversideFloor).orElseThrow();

        System.out.println(">>> seat map: " + map.getName());
        System.out.println(">>> now touching the seats list for the first time ...");
        for (SeatEntity seat : map.getSeats()) {
            System.out.println(">>>   seat " + seat.getRowLabel() + seat.getSeatNumber());
        }
        System.out.println(">>> seats in this map: " + map.getSeats().size());
    }
}
