package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Lesson 3: stubbing — teaching the mock one answer.
 * when(call).thenReturn(answer) reads: "when asked like this, answer that."
 */
class L3StubbingTest {

    @Test
    void holdWithRoom_allowsOneMoreSeat() {
        HoldRepository repository = mock(HoldRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(7)).thenReturn(Optional.of(hold)); // the teaching line

        SeatHoldService service = new SeatHoldService(repository, fixedClock);

        assertTrue(service.canAddSeat(7)); // 4 < 10, and 10:00 is before 10:10
    }

    @Test
    void fullHold_refusesAnotherSeat() {
        HoldRepository repository = mock(HoldRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        Hold fullHold = new Hold(7, 10, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(7)).thenReturn(Optional.of(fullHold));

        SeatHoldService service = new SeatHoldService(repository, fixedClock);

        assertFalse(service.canAddSeat(7)); // 10 seats is the cap
    }

    @Test
    void stubAnswersOnlyTheExactArgumentItWasTaught() {
        HoldRepository repository = mock(HoldRepository.class);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(7)).thenReturn(Optional.of(hold));

        System.out.println("findById(7) -> " + repository.findById(7));
        System.out.println("findById(8) -> " + repository.findById(8));
        assertTrue(repository.findById(8).isEmpty()); // 8 was never taught -> default
    }
}
