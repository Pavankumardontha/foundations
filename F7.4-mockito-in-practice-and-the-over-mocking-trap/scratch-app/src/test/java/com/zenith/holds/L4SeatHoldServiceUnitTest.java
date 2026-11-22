package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Lesson 4 / Subtask S1: the complete unit test for SeatHoldService.
 * One mock (the repository), one fixed clock, plain `new`, no Spring.
 */
class L4SeatHoldServiceUnitTest {

    /** The clock is frozen at 10:00. Every test below reads time from here. */
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    /** JUnit builds a fresh test instance per test, so each test gets a fresh mock. */
    private final HoldRepository repository = mock(HoldRepository.class);
    private final SeatHoldService service = new SeatHoldService(repository, FIXED_CLOCK);

    @Test
    void holdWithRoom_allowsOneMoreSeat() {
        Hold hold = new Hold(7, 4, NOW.plus(Duration.ofMinutes(10)));
        when(repository.findById(7)).thenReturn(Optional.of(hold));

        assertTrue(service.canAddSeat(7)); // 4 < 10, not expired
    }

    @Test
    void fullHold_refusesAnotherSeat() {
        Hold fullHold = new Hold(7, 10, NOW.plus(Duration.ofMinutes(10)));
        when(repository.findById(7)).thenReturn(Optional.of(fullHold));

        assertFalse(service.canAddSeat(7)); // seat cap reached
    }

    @Test
    void expiredHold_refusesAnotherSeat() {
        Hold expiredHold = new Hold(7, 4, NOW.minus(Duration.ofMinutes(5)));
        when(repository.findById(7)).thenReturn(Optional.of(expiredHold));

        assertFalse(service.canAddSeat(7)); // expired five minutes ago
    }

    @Test
    void missingHold_refusesSeat() {
        // No stubbing on purpose: the fresh mock answers Optional.empty,
        // which is exactly the "no such hold" scenario.
        assertFalse(service.canAddSeat(99));
    }
}
