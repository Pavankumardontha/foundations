package com.zenith.lifecycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 9: catch the swap in the act — same bean, two moments, two classes.
// Runs with BOTH profiles:
// mvn -q spring-boot:run -Dspring-boot.run.profiles=l8-transactional,l9-punchline
@Component
@Profile("l9-punchline")
class L9ProxyCatcher implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (beanName.equals("l8BookingService")) {
            System.out.println(">>> BEFORE init: " + beanName + " is " + bean.getClass().getName());
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (beanName.equals("l8BookingService")) {
            System.out.println(">>> AFTER  init: " + beanName + " is " + bean.getClass().getName());
        }
        return bean;
    }
}
