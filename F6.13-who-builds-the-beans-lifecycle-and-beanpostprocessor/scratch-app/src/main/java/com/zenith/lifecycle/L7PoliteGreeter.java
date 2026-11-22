package com.zenith.lifecycle;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 7: the honest bean. It knows nothing about any swap.
@Component
@Profile("l7-swap")
class L7PoliteGreeter implements L7Greeter {

    @Override
    public String greet() {
        return "good day";
    }
}
