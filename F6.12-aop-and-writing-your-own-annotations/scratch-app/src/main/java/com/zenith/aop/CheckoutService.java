package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

// Lesson 11 (subtask S6): two methods in one bean. The second is annotated.
// The first calls the second with a plain this.calculateTotal().
@Component
public class CheckoutService {

    // NOT annotated. Called from outside.
    public void checkout() throws InterruptedException {
        System.out.println("checkout: starting");
        this.calculateTotal();          // internal call - never leaves this object
        System.out.println("checkout: done");
    }

    @Timed("calculate-total")
    public void calculateTotal() throws InterruptedException {
        Thread.sleep(60);
        System.out.println("total calculated");
    }
}
