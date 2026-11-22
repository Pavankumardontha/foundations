package com.zenith.doubles;

/**
 * MOCK for the payment gateway.
 * refund is void and its effect lives outside the program,
 * so call-memory is the only evidence a test can use.
 */
public class MockPaymentGateway implements PaymentGateway {

    public int refundCallCount = 0;
    public long lastRefundedOrderId = -1;

    @Override
    public void refund(long orderId) {
        refundCallCount = refundCallCount + 1;
        lastRefundedOrderId = orderId;
    }
}
