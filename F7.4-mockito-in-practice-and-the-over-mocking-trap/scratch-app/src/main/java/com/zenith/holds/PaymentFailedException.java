package com.zenith.holds;

/** Domain exception: OUR word for "payment failed", independent of any provider. */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(long orderId, Throwable cause) {
        super("payment for order " + orderId + " failed", cause);
    }
}
