package com.zenith.doubles;

/**
 * Cancelling an order must trigger exactly one refund.
 * The refund call is the behaviour; there is no value to read back.
 */
public class OrderCancelService {

    private final PaymentGateway paymentGateway;

    public OrderCancelService(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    public void cancel(long orderId) {
        paymentGateway.refund(orderId);
    }
}
