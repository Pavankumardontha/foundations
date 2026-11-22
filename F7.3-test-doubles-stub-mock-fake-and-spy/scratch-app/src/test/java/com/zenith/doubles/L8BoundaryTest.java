package com.zenith.doubles;

import com.zenith.doubles.thirdparty.ProviderSdkClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lesson 8: mock at boundaries you own.
 * The service test doubles PaymentGateway — the interface WE defined.
 * The provider's SDK class appears in exactly one production class (the adapter),
 * and never in a service test.
 */
class L8BoundaryTest {

    @Test
    void serviceTestsDoubleTheOwnedBoundaryOnly() {
        MockPaymentGateway gateway = new MockPaymentGateway(); // double of OUR interface
        OrderCancelService service = new OrderCancelService(gateway);

        service.cancel(42);

        assertEquals(1, gateway.refundCallCount);
        // Note what is absent: no ProviderSdkClient, no JSON, no merchant id.
        // The service test knows nothing about the SDK. That is the rule working.
    }

    @Test
    void theAdapterSatisfiesTheOwnedInterface() {
        // Production wiring: the adapter wraps the real SDK client behind our interface.
        PaymentGateway gateway = new SdkPaymentGateway(new ProviderSdkClient(), "MERCHANT-77");

        // A smoke check only: it plugs in and runs. Its real test is an
        // integration test against the provider's sandbox, later.
        assertDoesNotThrow(() -> gateway.refund(42));
    }
}
