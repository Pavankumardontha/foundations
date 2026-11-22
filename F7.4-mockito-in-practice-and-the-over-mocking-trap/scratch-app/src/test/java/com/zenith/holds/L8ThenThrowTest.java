package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * Lesson 8 / Subtask S5: thenThrow — force a failure you cannot trigger
 * any other way, and assert the service converts it into the domain exception.
 */
class L8ThenThrowTest {

    private final PaymentGateway payments = mock(PaymentGateway.class);
    private final OrderPayService service = new OrderPayService(payments);

    @Test
    void happyPath_returnsProviderPaymentId() {
        when(payments.charge(new Money(4999))).thenReturn("pay_123");

        assertEquals("pay_123", service.pay(42, new Money(4999)));
    }

    @Test
    void gatewayTimeout_becomesOurDomainException() {
        // Force the failure: any charge now times out.
        when(payments.charge(any())).thenThrow(new GatewayTimeoutException());

        PaymentFailedException boom = assertThrows(PaymentFailedException.class,
                () -> service.pay(42, new Money(4999)));

        System.out.println("message: " + boom.getMessage());
        System.out.println("cause:   " + boom.getCause());

        assertEquals("payment for order 42 failed", boom.getMessage());
        assertInstanceOf(GatewayTimeoutException.class, boom.getCause());
    }
}
