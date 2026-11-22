package com.zenith.scopes;

import org.springframework.stereotype.Component;

@Component
public class GroupPricingRule implements PricingRule {
    @Override
    public int priceFor(int tickets) {
        if (tickets >= 3) {
            return tickets * 45;   // groups of three or more: 45 each
        }
        return tickets * 50;       // too small for the group discount: full price
    }
}
