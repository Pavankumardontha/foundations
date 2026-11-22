package com.zenith.holds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HoldsApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
        SeatHoldService holds = context.getBean(SeatHoldService.class);
        System.out.println(holds.describeHold(3));
        System.out.println(holds.describeHold(12));
        System.out.println(context.getBean(HoldExpiryNotifier.class).reminderLine());
    }
}
