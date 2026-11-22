package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Lesson 1: prove Mockito is installed, and watch it manufacture
 * an implementation of an interface in one line.
 */
class L1MockitoIsHereTest {

    @Test
    void mockitoCanManufactureARepository() {
        HoldRepository manufactured = Mockito.mock(HoldRepository.class);

        assertNotNull(manufactured); // an object exists; we wrote no class for it
        System.out.println("Mockito built an object of class: "
                + manufactured.getClass().getName());
    }
}
