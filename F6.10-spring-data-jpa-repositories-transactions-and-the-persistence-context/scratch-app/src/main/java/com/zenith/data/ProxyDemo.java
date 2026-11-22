package com.zenith.data;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 23: what object did Spring pass in for CheckoutService, and which way does a call go?
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=proxy
@Component
@Profile("proxy")
class ProxyDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final CheckoutService checkout;

    ProxyDemo(CheckoutService checkout) {
        this.checkout = checkout;
    }

    @Override
    public void run(String... args) {
        Object inside = AopProxyUtils.getSingletonTarget(checkout);
        System.out.println(">>> 1. the object Spring passed in for CheckoutService");
        System.out.println(">>>    its class:        " + checkout.getClass().getName());
        System.out.println(">>>    its parent class: " + checkout.getClass().getSuperclass().getName());
        System.out.println(">>>    is a proxy:       " + AopUtils.isAopProxy(checkout));
        System.out.println(">>>    its number:       " + System.identityHashCode(checkout));
        System.out.println(">>>    object inside it: " + inside.getClass().getName()
                + ", number " + System.identityHashCode(inside));

        System.out.println(">>> 2. calling checkout.persistOrder(...) from outside");
        checkout.persistOrder(SUFI_NIGHT, "meera@example.com", false);
    }
}
