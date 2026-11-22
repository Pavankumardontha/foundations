package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 1, the fast way: the service talks to a hand-written stand-in.
 * Same service, same rule, same checks. No database, no waiting.
 */
class L1StandInSpeedTest {

    @Test
    void holdWithFourSeatsCanTakeOneMore() {
        HoldRepository standIn = new StandInHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(standIn);
        assertTrue(service.canAddSeat(7));
    }

    @Test
    void holdWithTenSeatsIsFull() {
        HoldRepository standIn = new StandInHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(standIn);
        assertFalse(service.canAddSeat(8));
    }
}
