package com.zenith.autoconfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {

    // Our own ObjectMapper, configured properly:
    // - JavaTimeModule teaches it java.time types (Instant, LocalDate, ...)
    // - disabling WRITE_DATES_AS_TIMESTAMPS makes dates ISO-8601 strings, not numbers
    // Boot's jacksonObjectMapper recipe will find this bean and back off.
    @Bean
    ObjectMapper myObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
