package com.zenith.holds;

/**
 * Cancelling an order must trigger exactly one refund, and must never charge.
 * The refund call IS the behaviour; there is no value to read back.
 */
public class OrderCancelService {

    private final PaymentGateway payments;

    public OrderCancelService(PaymentGateway payments) {
        this.payments = payments;
    }

    public void cancel(long orderId) {
        payments.refund(orderId);
    }
}
