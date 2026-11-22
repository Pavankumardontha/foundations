package com.zenith.holds;

/**
 * Outbound boundary: talks to the payment provider's web service.
 * The real implementation moves actual money in a bank, outside the program.
 */
public interface PaymentGateway {

    /** Take the money. Returns the provider's payment id. */
    String charge(Money amount);

    /** Give the money for this order back. The effect happens at the bank. */
    void refund(long orderId);
}
