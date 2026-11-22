package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 3: a tiny dependency for the booking counter.
@Component
@Profile("l3-postconstruct")
class L3SeatMap {

    int seats() {
        return 40;
    }
}
