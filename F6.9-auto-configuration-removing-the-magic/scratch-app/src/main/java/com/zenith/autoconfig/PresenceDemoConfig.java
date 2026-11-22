package com.zenith.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PresenceDemoConfig {

    // Jackson IS on the classpath (the web starter brought it).
    // This condition passes, so this bean is created.
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    String jacksonPresentNote() {
        return "Jackson is present, so this bean was created";
    }

    // The Postgres driver is NOT on the classpath.
    // A class literal would not even compile, so the name is written as a string.
    // This condition fails, so Spring silently skips this bean.
    @Bean
    @ConditionalOnClass(name = "org.postgresql.Driver")
    String postgresPresentNote() {
        return "Postgres driver is present, so this bean was created";
    }
}
