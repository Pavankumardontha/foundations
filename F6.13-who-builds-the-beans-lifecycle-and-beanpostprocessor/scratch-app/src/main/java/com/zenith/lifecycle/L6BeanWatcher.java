package com.zenith.lifecycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 6: a post-processor that only WATCHES. Every bean passes through here.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l6-watch
@Component
@Profile("l6-watch")
class L6BeanWatcher implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        System.out.println(">>> watched: " + beanName);
        return bean;   // hand back the same object — observe only, change nothing
    }
}
