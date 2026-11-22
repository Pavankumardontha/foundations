package com.zenith.aop;

import com.zenith.common.Timed;
import org.springframework.stereotype.Component;

// Lesson 13 (subtask S8): a final method cannot be overridden,
// so the generated subclass cannot intercept it.
@Component
public class FinalMethodService {

    @Timed("final-export")
    public final void export() throws InterruptedException {
        Thread.sleep(50);
        System.out.println("export finished");
    }
}
