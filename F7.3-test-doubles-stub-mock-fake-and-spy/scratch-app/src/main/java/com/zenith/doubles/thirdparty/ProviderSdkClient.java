package com.zenith.doubles.thirdparty;

/**
 * PRETEND THIRD-PARTY CODE. Imagine this class arrived inside a payment provider's
 * SDK jar. You cannot edit it, you do not control when it changes, and it is final,
 * so you cannot even subclass it. You do not own this type.
 */
public final class ProviderSdkClient {

    /** Pretend: signs the payload and POSTs it to the provider over HTTPS. */
    public String createRefundRequest(String merchantId, String payloadJson) {
        return "{\"status\":\"QUEUED\"}";
    }
}
