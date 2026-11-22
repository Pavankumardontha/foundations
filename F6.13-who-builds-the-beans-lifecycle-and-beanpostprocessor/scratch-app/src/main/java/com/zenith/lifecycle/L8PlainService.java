package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 8: a service WITHOUT @Transactional — for comparison.
@Component
@Profile("l8-transactional")
class L8PlainService {

    public String ping() {
        return "pong";
    }
}
