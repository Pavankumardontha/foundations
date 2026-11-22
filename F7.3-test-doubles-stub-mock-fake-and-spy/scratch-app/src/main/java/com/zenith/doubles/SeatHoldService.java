package com.zenith.doubles;

import java.util.Optional;

/**
 * The class under test. Rule: a hold may take one more seat
 * only while it has fewer than 10 seats.
 */
public class SeatHoldService {

    private final HoldRepository repository;

    /** Constructor injection: the repository comes in through the constructor. */
    public SeatHoldService(HoldRepository repository) {
        this.repository = repository;
    }

    public boolean canAddSeat(long holdId) {
        Optional<Hold> found = repository.findById(holdId);
        if (found.isEmpty()) {
            return false; // no such hold, nothing to add to
        }
        return found.get().seats() < 10;
    }
}
