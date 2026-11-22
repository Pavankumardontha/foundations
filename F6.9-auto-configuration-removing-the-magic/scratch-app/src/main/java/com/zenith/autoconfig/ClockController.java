package com.zenith.autoconfig;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClockController {

    // A tiny response object with a date in it, so we can SEE
    // which ObjectMapper is doing the JSON work.
    record Now(String name, Instant time) {}

    @GetMapping("/now")
    Now now() {
        return new Now("scratch-app", Instant.now());
    }
}
