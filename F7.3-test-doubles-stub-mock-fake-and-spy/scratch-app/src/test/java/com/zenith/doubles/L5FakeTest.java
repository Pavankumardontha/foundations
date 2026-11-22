package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lesson 5: the fake has real behaviour, so it gets a few tests of its own (1-4),
 * and then it carries a whole multi-scenario service test on plain data (5).
 */
class L5FakeTest {

    @Test
    void findingBeforeAnySaveGivesEmpty() {
        FakeHoldRepository fake = new FakeHoldRepository();

        assertTrue(fake.findById(7).isEmpty());
    }

    @Test
    void whatYouSaveIsWhatYouFind() {
        FakeHoldRepository fake = new FakeHoldRepository();

        fake.save(new Hold(7, 4));

        // records compare by value, so two Hold(7, 4) are equal
        assertEquals(new Hold(7, 4), fake.findById(7).get());
    }

    @Test
    void savingTheSameIdOverwrites() {
        FakeHoldRepository fake = new FakeHoldRepository();

        fake.save(new Hold(7, 4));
        fake.save(new Hold(7, 9));

        assertEquals(9, fake.findById(7).get().seats());
    }

    @Test
    void deleteReallyDeletes() {
        FakeHoldRepository fake = new FakeHoldRepository();

        fake.save(new Hold(7, 4));
        fake.deleteById(7);

        assertTrue(fake.findById(7).isEmpty());
    }

    @Test
    void oneFakeCarriesThreeScenarios() {
        FakeHoldRepository fake = new FakeHoldRepository();
        SeatHoldService service = new SeatHoldService(fake);

        fake.save(new Hold(7, 4));   // a hold with room
        fake.save(new Hold(8, 10));  // a full hold

        assertTrue(service.canAddSeat(7));
        assertFalse(service.canAddSeat(8));
        assertFalse(service.canAddSeat(99)); // never saved, so genuinely missing
    }
}
