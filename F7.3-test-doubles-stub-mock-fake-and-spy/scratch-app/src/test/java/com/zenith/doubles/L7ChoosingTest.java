package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Lesson 7: the three ticketing situations from the task, each with the right choice.
 * Situation 1: a business rule — fake, state check.
 * Situation 2: an outbound call — mock, interaction check.
 * Situation 3: a value object — no double at all.
 */
class L7ChoosingTest {

    @Test
    void rejectsTheEleventhSeatWithAFake() {
        FakeHoldRepository fake = new FakeHoldRepository();
        SeatHoldService service = new SeatHoldService(fake);
        fake.save(new Hold(8, 10)); // given: a hold already at the 10-seat maximum

        assertFalse(service.canAddSeat(8)); // state check: the rule says no

        // The repository is incidental here. The fake only makes the hold exist,
        // so the test stays about the rule.
    }

    @Test
    void cancellingRefundsExactlyOnceWithAMock() {
        MockPaymentGateway gateway = new MockPaymentGateway();
        OrderCancelService service = new OrderCancelService(gateway);

        service.cancel(42);

        assertEquals(1, gateway.refundCallCount);      // the call IS the behaviour
        assertEquals(42, gateway.lastRefundedOrderId); // and it hit the right order
    }

    @Test
    void moneyAddsCorrectlyWithNoDouble() {
        Money oneHundred = new Money(100);
        Money fifty = new Money(50);

        assertEquals(new Money(150), oneHundred.plus(fifty));

        // No double anywhere. Money is a value object: cheap, real, deterministic.
    }
}
