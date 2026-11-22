package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

// Lesson 12, fix 2 (subtask S7): the bean injects ITSELF.
// "self" holds the PROXY, so self.calculateTotal() goes through the advice.
// @Lazy breaks the chicken-and-egg: at construction time the bean does not
// fully exist yet, so Spring hands a placeholder that resolves on first use.
@Component
public class SelfInjectedCheckoutService {

    private final SelfInjectedCheckoutService self;

    public SelfInjectedCheckoutService(@Lazy SelfInjectedCheckoutService self) {
        this.self = self;
    }

    public void checkout() throws InterruptedException {
        System.out.println("self-injected checkout: starting");
        self.calculateTotal();              // through the proxy this time, NOT this.
        System.out.println("self-injected checkout: done");
    }

    @Timed("self-injected-total")
    public void calculateTotal() throws InterruptedException {
        Thread.sleep(60);
        System.out.println("total calculated (self-injected version)");
    }
}
