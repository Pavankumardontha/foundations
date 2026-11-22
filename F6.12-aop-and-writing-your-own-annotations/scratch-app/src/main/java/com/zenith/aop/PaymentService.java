package com.zenith.aop;

import com.zenith.common.Idempotent;
import org.springframework.stereotype.Component;

@Component
public class PaymentService {

    private int charges = 0;

    // The demo key is fixed. In the real Phase 5, the key comes from the request.
    @Idempotent(key = "charge-order-42")
    public String charge() {
        charges++;
        System.out.println("Charging the card for real...");
        return "PAID-" + charges;
    }

    public int timesCharged() {
        return charges;
    }
}
