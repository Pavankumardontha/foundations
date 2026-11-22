package com.zenith.scopes;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuoteService {
    private final List<PricingRule> rules;   // ALL beans implementing PricingRule, in one list

    public QuoteService(List<PricingRule> rules) {
        this.rules = rules;
    }

    public String quote(int tickets) {
        String answer = tickets + " ticket(s):";
        int best = Integer.MAX_VALUE;   // the largest possible int, so any real price is smaller
        for (PricingRule rule : rules) {
            int price = rule.priceFor(tickets);
            answer = answer + " " + rule.getClass().getSimpleName() + " says " + price + ".";
            if (price < best) {
                best = price;
            }
        }
        return answer + " Best price: " + best;
    }
}
