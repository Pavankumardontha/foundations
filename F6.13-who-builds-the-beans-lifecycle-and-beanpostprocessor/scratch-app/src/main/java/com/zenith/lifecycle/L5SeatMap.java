package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 5: a tiny dependency, so we can see WHEN it gets filled.
@Component
@Profile("l5-map")
class L5SeatMap {

    int seats() {
        return 40;
    }
}
