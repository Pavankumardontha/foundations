package com.zenith.data;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Lesson 14: the same work done twice - without and with @Transactional - so the log shows the difference.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=tx
@Component
@Profile("tx")
class TransactionDemo implements CommandLineRunner {

    private static final UUID SUFI_NIGHT = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000005");

    private final EventService service;

    TransactionDemo(EventService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        System.out.println(">>> 1. calling describeWithoutTransaction");
        String first = service.describeWithoutTransaction(SUFI_NIGHT);
        System.out.println(">>>    returned: " + first);

        System.out.println(">>> 2. calling describeInOneTransaction");
        String second = service.describeInOneTransaction(SUFI_NIGHT);
        System.out.println(">>>    returned: " + second);
    }
}
