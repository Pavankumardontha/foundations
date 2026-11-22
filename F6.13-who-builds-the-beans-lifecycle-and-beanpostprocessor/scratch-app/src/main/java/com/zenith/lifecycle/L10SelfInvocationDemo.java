package com.zenith.lifecycle;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 10: the same method, reached by two paths — through the proxy, and past it.
@Component
@Profile("l10-selfinvocation")
class L10SelfInvocationDemo implements CommandLineRunner {

    private final L10NotifyService service;

    L10SelfInvocationDemo(L10NotifyService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> runner holds: " + service.getClass().getSimpleName());

        System.out.println(">>> --- call 1: service.saveNote(), from outside ---");
        service.saveNote();

        System.out.println(">>> --- call 2: service.saveNoteTwice(), which self-invokes saveNote() ---");
        service.saveNoteTwice();
    }
}
