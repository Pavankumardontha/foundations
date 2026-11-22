package com.zenith.holds;

/**
 * Takes payment for an order. Converts the provider's failure into our own
 * domain exception, so callers never depend on the provider's error types.
 */
public class OrderPayService {

    private final PaymentGateway payments;

    public OrderPayService(PaymentGateway payments) {
        this.payments = payments;
    }

    /** Returns the provider's payment id. */
    public String pay(long orderId, Money amount) {
        try {
            return payments.charge(amount);
        } catch (GatewayTimeoutException e) {
            throw new PaymentFailedException(orderId, e);
        }
    }
}
