package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/**
 * Lesson 6: verify — asserting that a call happened (or never did).
 * Use it only when the interaction genuinely IS the behaviour.
 */
class L6VerifyTest {

    private final PaymentGateway payments = mock(PaymentGateway.class);
    private final OrderCancelService service = new OrderCancelService(payments);

    @Test
    void cancel_refundsTheOrder() {
        service.cancel(42);

        verify(payments).refund(42); // this exact call happened (once)
    }

    @Test
    void cancellingTwice_refundsTwice() {
        service.cancel(42);
        service.cancel(42);

        verify(payments, times(2)).refund(42);
    }

    @Test
    void cancel_neverCharges() {
        service.cancel(42);

        verify(payments, never()).charge(any()); // no charge, with any amount at all
    }

    @Test
    void verifyFailure_readTheMessage() {
        service.cancel(42);

        AssertionError boom = assertThrows(AssertionError.class,
                () -> verify(payments).refund(99)); // wrong id on purpose

        System.out.println("--- real verify failure message ---");
        System.out.println(boom.getMessage());
    }
}
