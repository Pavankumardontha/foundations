package com.zenith.aop;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;

// Lesson 14 (subtask S9): switch on Spring's own @Async machinery.
// @EnableAsync registers Spring's built-in advice that reads @Async marks.
@Configuration
@EnableAsync
@Profile("l14-async")
public class L14AsyncConfig {
}
