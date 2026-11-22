package com.zenith.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

// Lesson 6: how big is "everything"? Count the beans in the full context.
@SpringBootTest
class L6BeanCountTest {

    @Autowired
    ApplicationContext context;

    @Test
    void countTheBeans() {
        System.out.println(">>> Beans in the FULL context: "
                + context.getBeanDefinitionCount());
    }
}
