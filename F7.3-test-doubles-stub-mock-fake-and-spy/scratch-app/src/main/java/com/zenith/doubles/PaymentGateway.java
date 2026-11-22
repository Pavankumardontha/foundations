package com.zenith.doubles;

/**
 * Outbound boundary: asks the payment provider to return money.
 * The real implementation would call the provider's web service.
 * refund returns nothing — the effect happens in a bank, outside the program.
 */
public interface PaymentGateway {

    void refund(long orderId);
}
