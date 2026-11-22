package com.zenith.data;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;

// Lesson 3: what object did Spring pass in for the EventRepository interface?
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=get-class
@Component
@Profile("get-class")
class GetClassDemo implements CommandLineRunner {

    private final EventRepository events;

    GetClassDemo(EventRepository events) {
        this.events = events;
    }

    @Override
    public void run(String... args) {
        Class<?> type = events.getClass();
        System.out.println(">>> class of the object     : " + type.getName());
        System.out.println(">>> made by Java's Proxy?   : " + Proxy.isProxyClass(type));
        System.out.println(">>> is a SimpleJpaRepository: " + (events instanceof SimpleJpaRepository));
        System.out.println(">>> target behind it        : " + AopProxyUtils.ultimateTargetClass(events).getName());
        System.out.println(">>> interfaces it implements:");
        for (Class<?> each : type.getInterfaces()) {
            System.out.println(">>>     " + each.getName());
        }
        System.out.println(">>> count() through it      : " + events.count());
        System.out.println(">>> println(events) shows   : " + events);
    }
}
