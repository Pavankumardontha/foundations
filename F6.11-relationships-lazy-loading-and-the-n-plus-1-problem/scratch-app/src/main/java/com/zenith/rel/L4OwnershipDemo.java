package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Lesson 4: prove that the database records only the owning side.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l4-ownership
@Component
@Profile("l4-ownership")
class L4OwnershipDemo implements CommandLineRunner {

    private final L4OwnershipService experiments;

    L4OwnershipDemo(L4OwnershipService experiments) {
        this.experiments = experiments;
    }

    @Override
    public void run(String... args) {
        experiments.cleanUpRowC();              // make reruns start from the same state
        experiments.report("start           ");

        experiments.addToListOnly();            // commit happens as the method returns
        experiments.report("after list-only ");

        experiments.setOwningSideOnly();
        experiments.report("after owning    ");

        experiments.addBothSides();
        experiments.report("after helper    ");
    }
}
