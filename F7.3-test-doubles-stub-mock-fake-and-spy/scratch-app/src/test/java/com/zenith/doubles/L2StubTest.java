package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 2: the service under test is fed by stubs.
 * Every assert looks at the service's answer. No assert ever looks at the stub.
 */
class L2StubTest {

    @Test
    void holdWithFourSeatsCanTakeOneMore() {
        HoldRepository stub = new StubHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(stub);
        assertTrue(service.canAddSeat(7));
    }

    @Test
    void holdWithTenSeatsIsFull() {
        HoldRepository stub = new StubHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(stub);
        assertFalse(service.canAddSeat(8));
    }

    @Test
    void missingHoldCannotTakeASeat() {
        HoldRepository stub = new StubMissingHoldRepository();
        SeatHoldService service = new SeatHoldService(stub);
        assertFalse(service.canAddSeat(99)); // id 99 exists nowhere
    }
}
