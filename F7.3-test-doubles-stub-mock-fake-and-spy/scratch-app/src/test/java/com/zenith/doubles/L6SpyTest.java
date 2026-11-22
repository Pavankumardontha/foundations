package com.zenith.doubles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Lesson 6: the spy wraps a real repository (here: the fake, which is real working
 * code), forwards every call, and records the arguments on the way through.
 */
class L6SpyTest {

    @Test
    void forwardsToTheRealRepositoryUnderneath() {
        SpyHoldRepository spy = new SpyHoldRepository(new FakeHoldRepository());

        spy.save(new Hold(7, 4));

        // The save really happened in the wrapped fake, so it can be found again.
        assertEquals(new Hold(7, 4), spy.findById(7).get());
    }

    @Test
    void recordsEveryCallAsItPassesThrough() {
        SpyHoldRepository spy = new SpyHoldRepository(new FakeHoldRepository());
        SeatHoldService service = new SeatHoldService(spy);

        spy.save(new Hold(7, 4));
        service.canAddSeat(7);
        service.canAddSeat(7);

        // Lists compare by contents.
        assertEquals(List.of(7L, 7L), spy.findByIdIds); // two lookups, both for id 7
        assertEquals(List.of(new Hold(7, 4)), spy.savedHolds);
    }

    @Test
    void realAnswersAndMemoryTogether() {
        SpyHoldRepository spy = new SpyHoldRepository(new FakeHoldRepository());
        SeatHoldService service = new SeatHoldService(spy);

        spy.save(new Hold(8, 10));

        assertFalse(service.canAddSeat(8));         // state check: real rule on real data
        assertEquals(List.of(8L), spy.findByIdIds); // interaction check: what was asked
    }
}
