package com.zenith.scopes;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class TieredPricingRule implements PricingRule {
    @Override
    public int priceFor(int tickets) {
        if (tickets <= 2) {
            return tickets * 50;            // first two tickets: full price, 50 each
        }
        return 100 + (tickets - 2) * 40;    // from the third ticket on: 40 each
    }
}
