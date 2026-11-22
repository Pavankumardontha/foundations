package com.zenith.scopes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SeatHoldService {
    private static final Duration HOLD_TIME = Duration.ofMinutes(10);

    private final Clock clock;   // asks the clock, never the computer directly

    public SeatHoldService(Clock clock) {
        this.clock = clock;
    }

    public boolean isExpired(Instant heldAt) {
        Instant now = clock.instant();
        return now.isAfter(heldAt.plus(HOLD_TIME));
    }
}
