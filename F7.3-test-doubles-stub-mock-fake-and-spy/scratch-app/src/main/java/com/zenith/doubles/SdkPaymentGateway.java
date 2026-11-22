package com.zenith.doubles;

import com.zenith.doubles.thirdparty.ProviderSdkClient;

/**
 * The wrapper (adapter): the ONLY class allowed to touch the provider's SDK.
 * It implements the boundary interface we own, and translates our need
 * ("refund order 42") into the SDK's language (merchant ids, JSON strings).
 * Deliberately too thin to unit test; it gets an integration test later.
 */
public class SdkPaymentGateway implements PaymentGateway {

    private final ProviderSdkClient client;
    private final String merchantId;

    public SdkPaymentGateway(ProviderSdkClient client, String merchantId) {
        this.client = client;
        this.merchantId = merchantId;
    }

    @Override
    public void refund(long orderId) {
        client.createRefundRequest(merchantId, "{\"orderId\":" + orderId + "}");
    }
}
