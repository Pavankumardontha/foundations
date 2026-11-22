package com.zenith.testing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuestListTest {

    private List<String> guests;

    @BeforeEach
    void createGuestList() {
        guests = new ArrayList<>();
        guests.add("Asha");
        System.out.println("setup: fresh guest list with " + guests.size() + " guest");
    }

    @Test
    void testA_addsAGuest() {
        guests.add("Ravi");
        System.out.println("test A sees " + guests.size() + " guests");
        assertEquals(2, guests.size());
    }

    @Test
    void testB_countsOneGuestAtStart() {
        System.out.println("test B sees " + guests.size() + " guest");
        assertEquals(1, guests.size());
    }
}
