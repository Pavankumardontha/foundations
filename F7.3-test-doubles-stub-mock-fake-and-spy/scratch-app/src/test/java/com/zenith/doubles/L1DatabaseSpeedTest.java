package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 1, the painful way: the service talks to the (pretend) real database.
 * Every findById costs 1.5 seconds.
 */
class L1DatabaseSpeedTest {

    @Test
    void holdWithFourSeatsCanTakeOneMore() {
        SeatHoldService service = new SeatHoldService(new DatabaseHoldRepository());
        assertTrue(service.canAddSeat(7)); // hold 7 has 4 seats in the database
    }

    @Test
    void holdWithTenSeatsIsFull() {
        SeatHoldService service = new SeatHoldService(new DatabaseHoldRepository());
        assertFalse(service.canAddSeat(8)); // hold 8 has 10 seats in the database
    }
}
