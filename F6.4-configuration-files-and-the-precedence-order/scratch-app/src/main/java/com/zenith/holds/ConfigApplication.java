package com.zenith.holds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class ConfigApplication {
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(ConfigApplication.class, args);
        ConfigReporter reporter = context.getBean(ConfigReporter.class);
        System.out.println(reporter.ttlLine());
        System.out.println(reporter.ttlDecodedLine());
        System.out.println(reporter.maxSeatsLine());
        System.out.println(reporter.providerUrlLine());
        System.out.println(reporter.currenciesLine());
    }
}
