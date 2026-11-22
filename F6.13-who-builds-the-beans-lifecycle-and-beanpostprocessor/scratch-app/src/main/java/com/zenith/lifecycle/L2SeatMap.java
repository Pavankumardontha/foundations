package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 2: a tiny dependency for the booking counter.
@Component
@Profile("l2-too-early")
class L2SeatMap {

    int seats() {
        return 40;
    }
}
