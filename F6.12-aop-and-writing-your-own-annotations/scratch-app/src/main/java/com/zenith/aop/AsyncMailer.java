package com.zenith.aop;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Lesson 14 (subtask S9): Spring's own @Async, used exactly like our @Timed.
@Component
public class AsyncMailer {

    @Async
    public void sendWelcomeMail() {
        System.out.println("sending welcome mail on thread: "
                + Thread.currentThread().getName());
    }

    // The same trap as Lesson 11: a plain this.… call never crosses the proxy.
    public void register() {
        System.out.println("register() running on thread:   "
                + Thread.currentThread().getName());
        this.sendWelcomeMail(); // self-invocation
    }
}
