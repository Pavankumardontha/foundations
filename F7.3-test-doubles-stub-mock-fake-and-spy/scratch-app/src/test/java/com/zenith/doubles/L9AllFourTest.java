package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lesson 9: all four doubles in one test class, now with Mockito.
 * Mockito's mock() makes a blank double. What you DO with it decides its role:
 *   thenReturn(...)  -> it acts as a stub   -> assert state.
 *   verify(...)      -> it acts as a mock   -> assert the interaction.
 *   spy(realThing)   -> it acts as a spy    -> real behaviour, calls recorded.
 * Mockito cannot make a fake: a fake is a real implementation, written by hand.
 */
class L9AllFourTest {

    @Test
    void stubRoleCannedAnswerStateAssert() {
        HoldRepository repository = mock(HoldRepository.class);          // blank double
        when(repository.findById(7)).thenReturn(Optional.of(new Hold(7, 4))); // canned

        SeatHoldService service = new SeatHoldService(repository);

        assertTrue(service.canAddSeat(7)); // state assertion suits a stub
    }

    @Test
    void mockRoleCallMemoryInteractionAssert() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        OrderCancelService service = new OrderCancelService(gateway);

        service.cancel(42);

        verify(gateway, times(1)).refund(42); // interaction assertion suits a mock
    }

    @Test
    void fakeRoleStaysHandWrittenStateAssert() {
        FakeHoldRepository fake = new FakeHoldRepository(); // Mockito cannot make fakes
        SeatHoldService service = new SeatHoldService(fake);

        fake.save(new Hold(8, 10));

        assertFalse(service.canAddSeat(8)); // state assertion via genuine read-back
    }

    @Test
    void spyRoleRealBehaviourPlusCallRecord() {
        HoldRepository repository = spy(new FakeHoldRepository()); // wraps a real object

        SeatHoldService service = new SeatHoldService(repository);
        repository.save(new Hold(7, 4));   // forwarded: really stored underneath

        assertTrue(service.canAddSeat(7));         // state: real answer from underneath
        verify(repository, times(1)).findById(7);  // interaction: the call was recorded
    }
}
