package com.zenith.lifecycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LifecycleApplication {

    public static void main(String[] args) {
        System.out.println(">>> main: before SpringApplication.run — no beans exist yet");
        SpringApplication.run(LifecycleApplication.class, args);
        System.out.println(">>> main: run() returned — every bean is built and ready");
    }
}
