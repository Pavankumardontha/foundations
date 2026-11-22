package com.zenith.holds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class HoldsApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(HoldsApplication.class, args);
        SeatHoldService seatHolds = context.getBean(SeatHoldService.class);
        OrderService orders = context.getBean(OrderService.class);
        System.out.println(seatHolds.hold("A1"));
        System.out.println(orders.purchase("B7"));
        System.out.println(seatHolds.hold("B7"));
        System.out.println(orders.purchase("A1"));
    }
}
