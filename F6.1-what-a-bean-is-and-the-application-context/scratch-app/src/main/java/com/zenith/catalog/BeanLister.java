package com.zenith.catalog;

import java.util.Arrays;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
class BeanLister implements CommandLineRunner {
    private final ApplicationContext context;

    BeanLister(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(String... args) {
        Arrays.stream(context.getBeanDefinitionNames())
              .sorted()
              .forEach(System.out::println);
    }
}
