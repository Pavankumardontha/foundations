package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class FlatPricingRule implements PricingRule {
    @Override
    public int priceFor(int tickets) {
        return tickets * 50;   // every ticket costs 50
    }
}
