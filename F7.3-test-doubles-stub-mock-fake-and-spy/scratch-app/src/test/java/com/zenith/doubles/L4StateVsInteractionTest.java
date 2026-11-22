package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Lesson 4: the same scenario tested two ways.
 * Scenario: a hold already has 10 seats, the legal maximum. One more must be refused.
 * The state check asserts on the resulting value.
 * The interaction check asserts on the calls that were made.
 */
class L4StateVsInteractionTest {

    @Test
    void stateCheckFullHoldGetsNo() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(mock);

        boolean answer = service.canAddSeat(8);

        assertFalse(answer); // state: the resulting value says no
    }

    @Test
    void interactionCheckRepositoryAskedOnce() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(8, 10));
        SeatHoldService service = new SeatHoldService(mock);

        service.canAddSeat(8);

        assertEquals(1, mock.findByIdCallCount); // interaction: the call was made once
    }
}
