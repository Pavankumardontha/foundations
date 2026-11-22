package com.zenith.holds;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * The class under test for this task.
 * Rule 1: a new hold expires ten minutes after it is placed.
 * Rule 2: a hold may take one more seat only if the hold exists,
 * has not expired, and has fewer than 10 seats.
 */
public class SeatHoldService {

    private static final Duration HOLD_TIME = Duration.ofMinutes(10);

    private final HoldRepository repository;
    private final Clock clock; // asks the clock, never the computer directly

    /** Constructor injection: both needs come in through the constructor. */
    public SeatHoldService(HoldRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Place a new hold. It expires HOLD_TIME from now — that is rule 1. */
    public Hold placeHold(long id, int seats) {
        Hold hold = new Hold(id, seats, clock.instant().plus(HOLD_TIME));
        repository.save(hold);
        return hold;
    }

    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false; // no such hold, nothing to add to
        }
        Hold hold = found.get();
        boolean expired = clock.instant().isAfter(hold.expiresAt());
        return !expired && hold.seats() < 10;
    }
}
