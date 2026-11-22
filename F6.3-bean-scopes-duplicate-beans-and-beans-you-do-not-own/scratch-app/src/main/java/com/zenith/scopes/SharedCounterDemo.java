package com.zenith.scopes;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Two workers hammer the one shared CallCounterService at the same time.
 * If the counter were safe, the final number would be exactly 2000000.
 */
public class SharedCounterDemo {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = SpringApplication.run(
                ScopesApplication.class, "--spring.main.web-application-type=none");
        try {
            CallCounterService counter = context.getBean(CallCounterService.class);

            Runnable oneMillionCalls = () -> {
                for (int i = 0; i < 1000000; i++) {
                    counter.record();
                }
            };

            Thread workerA = new Thread(oneMillionCalls);
            Thread workerB = new Thread(oneMillionCalls);
            workerA.start();   // both workers now run at the same time...
            workerB.start();
            workerA.join();    // ...and main waits here until both are done
            workerB.join();

            System.out.println("calls made:    2000000");
            System.out.println("counter shows: " + counter.current());
            System.out.println("lost updates:  " + (2000000 - counter.current()));
        } finally {
            context.close();
        }
    }
}
