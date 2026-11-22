package com.zenith.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Lesson 2: start the WHOLE application inside the test JVM.
// Every bean exists, and the database connection is real.
@SpringBootTest
class L2FullContextStartsTest {

    @Autowired
    EventService service;

    @Autowired
    EventRepository repository;

    @Test
    void theWholeApplicationIsHere() {
        assertNotNull(service);
        assertNotNull(repository);
        // Real SQL against the in-memory database: proves the datasource is live.
        assertEquals(0, repository.count());
    }
}
