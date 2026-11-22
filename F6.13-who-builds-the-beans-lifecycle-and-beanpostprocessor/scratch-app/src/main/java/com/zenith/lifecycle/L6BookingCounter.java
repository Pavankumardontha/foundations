package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 6: a bean that needs another bean — watch the order they pass the watcher.
@Component
@Profile("l6-watch")
class L6BookingCounter {

    private final L6SeatMap seatMap;

    L6BookingCounter(L6SeatMap seatMap) {
        this.seatMap = seatMap;
    }
}
