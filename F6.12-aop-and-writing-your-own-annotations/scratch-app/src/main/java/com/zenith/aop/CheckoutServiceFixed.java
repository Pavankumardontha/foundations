package com.zenith.aop;

import org.springframework.stereotype.Component;

// Lesson 12, fix 1 (subtask S7): the caller now injects the other bean.
// What it receives is that bean's PROXY - so the call gets advice.
@Component
public class CheckoutServiceFixed {

    private final TotalCalculator totalCalculator;

    public CheckoutServiceFixed(TotalCalculator totalCalculator) {
        this.totalCalculator = totalCalculator;
    }

    public void checkout() throws InterruptedException {
        System.out.println("fixed checkout: starting");
        totalCalculator.calculateTotal();   // crosses a bean boundary -> proxy -> advice
        System.out.println("fixed checkout: done");
    }
}
