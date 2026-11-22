package com.zenith.lifecycle;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 3: @PostConstruct — Spring's "everything is filled, go ahead" hook.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l3-postconstruct
@Component
@Profile("l3-postconstruct")
class L3BookingCounter implements CommandLineRunner {

    @Autowired
    private L3SeatMap seatMap;

    private int freeSeats;   // filled by the setup method

    L3BookingCounter() {
        System.out.println(">>> constructor:    seatMap = " + seatMap);
    }

    @PostConstruct
    void prepare() {
        this.freeSeats = seatMap.seats();   // safe: injection is finished here
        System.out.println(">>> @PostConstruct: seatMap = " + seatMap + " — setup done, " + freeSeats + " seats free");
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> run():          the app is working, freeSeats = " + freeSeats);
    }
}
