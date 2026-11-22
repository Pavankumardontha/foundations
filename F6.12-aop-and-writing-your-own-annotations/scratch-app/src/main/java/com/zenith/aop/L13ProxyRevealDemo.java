package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 13 demo. Run with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l13-proxy
@Component
@Profile("l13-proxy")
public class L13ProxyRevealDemo implements CommandLineRunner {

    private final BookingService bookingService;
    private final TimingAspect timingAspect;
    private final FinalMethodService finalMethodService;

    public L13ProxyRevealDemo(BookingService bookingService,
                              TimingAspect timingAspect,
                              FinalMethodService finalMethodService) {
        this.bookingService = bookingService;
        this.timingAspect = timingAspect;
        this.finalMethodService = finalMethodService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- what did Spring actually hand us? ---");
        System.out.println("bookingService class:      " + bookingService.getClass().getName());
        System.out.println("bookingService superclass: " + bookingService.getClass().getSuperclass().getName());
        System.out.println("timingAspect class:        " + timingAspect.getClass().getName());
        System.out.println("finalMethodService class:  " + finalMethodService.getClass().getName());

        System.out.println("--- calling the final @Timed method ---");
        finalMethodService.export();
        System.out.println("(look: no timing line above)");
    }
}
