package com.zenith.scopes;

import java.time.Clock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
public class PlatformController {
    private final Clock clock;
    private final RestClient paymentsClient;

    public PlatformController(Clock clock, RestClient paymentsClient) {
        this.clock = clock;
        this.paymentsClient = paymentsClient;
    }

    @GetMapping("/platform")
    public PlatformReport platform() {
        return new PlatformReport(
                clock.instant().toString(),
                paymentsClient.getClass().getSimpleName());
    }

    public record PlatformReport(String timeNow, String paymentsClientType) {
    }
}
