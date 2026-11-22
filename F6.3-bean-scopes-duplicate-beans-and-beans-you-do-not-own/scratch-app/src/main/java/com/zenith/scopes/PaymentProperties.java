package com.zenith.scopes;

import org.springframework.stereotype.Component;

/** Our own class, so a normal annotation works. A later task replaces this with real configuration. */
@Component
public class PaymentProperties {
    public String providerUrl() {
        return "https://payments.example.com";
    }
}
