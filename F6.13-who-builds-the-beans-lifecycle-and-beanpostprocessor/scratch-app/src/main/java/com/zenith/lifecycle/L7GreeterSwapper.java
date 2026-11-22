package com.zenith.lifecycle;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 7: a post-processor that uses its licence to SWAP.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l7-swap
@Component
@Profile("l7-swap")
class L7GreeterSwapper implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof L7Greeter original) {
            System.out.println(">>> swapper: replacing '" + beanName + "' with a shouting stand-in");
            return new L7ShoutingGreeter(original);   // the context keeps THIS object
        }
        return bean;
    }
}
