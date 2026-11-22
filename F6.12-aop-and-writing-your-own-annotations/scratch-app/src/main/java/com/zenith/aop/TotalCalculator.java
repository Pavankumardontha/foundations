package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

// Lesson 12, fix 1 (subtask S7): the annotated method moved into its own bean.
// Every call to it now arrives from another bean, so it always crosses the proxy.
@Component
public class TotalCalculator {

    @Timed("extracted-total")
    public void calculateTotal() throws InterruptedException {
        Thread.sleep(60);
        System.out.println("total calculated (own-bean version)");
    }
}
