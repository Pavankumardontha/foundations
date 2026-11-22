package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Lesson 6: the grown-up join table as an entity. Each grant is a real object
// carrying data about the link itself: who granted the role, and when.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l6-role-grants
@Component
@Profile("l6-role-grants")
class L6RoleGrantsDemo implements CommandLineRunner {

    private final RoleGrantRepository grants;

    L6RoleGrantsDemo(RoleGrantRepository grants) {
        this.grants = grants;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (RoleGrantEntity grant : grants.findAll()) {
            System.out.println(">>> " + grant.getRole().getName()
                    + " -> " + grant.getUser().getDisplayName()
                    + " | granted by " + grant.getGrantedBy()
                    + " on " + grant.getGrantedAt().toLocalDate());
        }
    }
}
