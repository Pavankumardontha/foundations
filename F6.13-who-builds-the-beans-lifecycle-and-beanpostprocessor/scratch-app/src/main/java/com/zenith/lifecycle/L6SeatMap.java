package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 6: a bean with no dependencies.
@Component
@Profile("l6-watch")
class L6SeatMap {

    int seats() {
        return 40;
    }
}
