package com.zenith.holds;

/** Thrown by the payment provider's side when their service does not answer in time. */
public class GatewayTimeoutException extends RuntimeException {

    public GatewayTimeoutException() {
        super("payment provider did not answer within 30 seconds");
    }
}
