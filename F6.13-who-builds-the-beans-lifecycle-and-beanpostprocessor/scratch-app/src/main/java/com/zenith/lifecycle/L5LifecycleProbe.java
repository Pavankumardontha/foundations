package com.zenith.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 5: one bean that reports every stage of its own birth.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l5-map
@Component
@Profile("l5-map")
class L5LifecycleProbe implements BeanNameAware, CommandLineRunner {

    @Autowired
    private L5SeatMap seatMap;

    L5LifecycleProbe() {
        System.out.println(">>> stage 1  instantiate:  constructor runs, seatMap = " + seatMap);
    }

    // stage 2 (populate dependencies) has no hook — but its effect shows in stage 3:

    @Override
    public void setBeanName(String name) {
        System.out.println(">>> stage 3  aware:        my name is '" + name + "', seatMap = " + seatMap);
    }

    // stage 4 (post-process BEFORE initialization) belongs to BeanPostProcessor — lesson 6.

    @PostConstruct
    void init() {
        System.out.println(">>> stage 5  initialize:   @PostConstruct runs, " + seatMap.seats() + " seats");
    }

    // stage 6 (post-process AFTER initialization) belongs to BeanPostProcessor — lesson 6.

    @Override
    public void run(String... args) {
        System.out.println(">>> stage 7  ready:        the context holds me, the app uses me");
    }

    @PreDestroy
    void destroy() {
        System.out.println(">>> destruction:           @PreDestroy runs at context close");
    }
}
