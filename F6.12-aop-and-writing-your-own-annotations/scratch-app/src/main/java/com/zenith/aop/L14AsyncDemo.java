package com.zenith.aop;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 14 demo. Run with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l14-async
@Component
@Profile("l14-async")
public class L14AsyncDemo implements CommandLineRunner {

    private final AsyncMailer mailer;

    public L14AsyncDemo(AsyncMailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("mailer class: " + mailer.getClass().getName());
        System.out.println("main thread:  " + Thread.currentThread().getName());

        System.out.println("--- calling @Async sendWelcomeMail() from OUTSIDE ---");
        mailer.sendWelcomeMail();
        Thread.sleep(300); // give the background thread time to print

        System.out.println("--- calling register(), which calls this.sendWelcomeMail() INSIDE ---");
        mailer.register();
        Thread.sleep(300);
    }
}
