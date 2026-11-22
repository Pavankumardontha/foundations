package com.zenith.events;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Lesson 8, side measurement: how big is the WEB SLICE context?
// Compare with L6BeanCountTest's full-context number.
@WebMvcTest(EventController.class)
class L8SliceBeanCountTest {

    @Autowired
    ApplicationContext context;

    @MockitoBean
    EventService service;   // the stand-in that lets the slice start at all

    @Test
    void countTheBeans() {
        System.out.println(">>> Beans in the WEB SLICE context: "
                + context.getBeanDefinitionCount());
    }
}
