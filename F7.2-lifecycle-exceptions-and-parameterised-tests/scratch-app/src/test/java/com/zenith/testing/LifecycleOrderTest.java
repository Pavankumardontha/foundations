package com.zenith.testing;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LifecycleOrderTest {

    @BeforeAll
    static void onceBeforeEverything() {
        System.out.println("BEFORE ALL - once, before any test object exists");
    }

    @BeforeEach
    void setUp() {
        System.out.println("  setup");
    }

    @Test
    void firstTest() {
        System.out.println("    first test");
        assertEquals(4, 2 + 2);
    }

    @Test
    void secondTest() {
        System.out.println("    second test");
        assertEquals(6, 2 * 3);
    }

    @AfterEach
    void cleanUp() {
        System.out.println("  cleanup");
    }

    @AfterAll
    static void onceAfterEverything() {
        System.out.println("AFTER ALL - once, after the last test");
    }
}
