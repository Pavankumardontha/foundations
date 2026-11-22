package com.zenith.scopes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CounterController {
    private final CallCounterService counter;

    public CounterController(CallCounterService counter) {
        this.counter = counter;
    }

    @GetMapping("/count")
    public String count() {
        return "call number " + counter.record();
    }
}
