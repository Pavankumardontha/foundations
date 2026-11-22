package com.zenith.rel;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 5: load ONE user, then read the roles that live behind the join table.
// Runs only with: mvn -q spring-boot:run -Dspring-boot.run.profiles=l5-user-roles
@Component
@Profile("l5-user-roles")
class L5UserRolesDemo implements CommandLineRunner {

    private final UserRepository users;

    L5UserRolesDemo(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public void run(String... args) {
        UUID meeraId = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
        UserEntity user = users.findById(meeraId).orElseThrow();

        System.out.println(">>> user: " + user.getDisplayName()
                + " (" + user.getEmail() + ")");
        System.out.println(">>> now touching the roles set for the first time ...");
        for (RoleEntity role : user.getRoles()) {
            System.out.println(">>>   role: " + role.getName());
        }
    }
}
