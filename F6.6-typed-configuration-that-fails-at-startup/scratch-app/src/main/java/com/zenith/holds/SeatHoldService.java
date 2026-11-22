package com.zenith.holds;

import org.springframework.stereotype.Service;

/**
 * Places holds on seats. Subtasks S3+S4: configuration arrives as one
 * typed object, injected like any other bean. No string keys here.
 */
@Service
public class SeatHoldService {

    private final HoldProperties properties;

    public SeatHoldService(HoldProperties properties) {
        this.properties = properties;
    }

    public String describeHold(int requestedSeats) {
        if (requestedSeats > properties.maxSeatsPerOrder()) {
            return "REFUSED: " + requestedSeats + " seats requested, limit is "
                    + properties.maxSeatsPerOrder();
        }
        return "HELD: " + requestedSeats + " seats for "
                + properties.ttl().toMinutes() + " minutes";
    }
}
