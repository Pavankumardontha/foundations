package com.zenith.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanupTest {

    @BeforeEach
    void setUp() {
        System.out.println("setup");
    }

    @Test
    void firstTest() {
        System.out.println("first test runs");
        assertEquals(4, 2 + 2);
    }

    @Test
    void secondTest() {
        System.out.println("second test runs");
        assertEquals(6, 2 * 3);
    }

    @AfterEach
    void cleanUp() {
        System.out.println("cleanup");
    }
}
