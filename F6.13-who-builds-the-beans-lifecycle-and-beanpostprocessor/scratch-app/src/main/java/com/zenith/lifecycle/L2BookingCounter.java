package com.zenith.lifecycle;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 2: the constructor runs too early — the field is not filled yet.
// Field injection is used ON PURPOSE here, to make that moment visible.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l2-too-early
@Component
@Profile("l2-too-early")
class L2BookingCounter implements CommandLineRunner {

    @Autowired
    private L2SeatMap seatMap;

    L2BookingCounter() {
        System.out.println(">>> constructor: seatMap = " + seatMap);
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> run():       seatMap = " + seatMap + " — " + seatMap.seats() + " seats");
    }
}
