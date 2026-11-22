package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 3: the mock remembers how it was called, and the test asserts on that memory.
 * Note the variable type: MockHoldRepository, not HoldRepository.
 * The recording fields are not part of the interface, so the test needs the concrete type.
 */
class L3MockTest {

    @Test
    void looksUpTheHoldExactlyOnce() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(mock);

        service.canAddSeat(7);

        assertEquals(1, mock.findByIdCallCount); // asserting on the calls, not the result
    }

    @Test
    void asksForTheIdItWasGiven() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(mock);

        service.canAddSeat(7);

        assertEquals(7, mock.lastAskedId);
    }

    @Test
    void stillAnswersLikeAStub() {
        MockHoldRepository mock = new MockHoldRepository(new Hold(7, 4));
        SeatHoldService service = new SeatHoldService(mock);

        assertTrue(service.canAddSeat(7)); // a mock can still serve a plain state check
    }
}
