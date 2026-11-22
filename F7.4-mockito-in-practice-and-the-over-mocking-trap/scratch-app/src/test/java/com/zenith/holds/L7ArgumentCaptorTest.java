package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Lesson 7 / Subtask S4: ArgumentCaptor — catch the object the code built
 * and assert a real rule about it, not just that a method was called.
 */
class L7ArgumentCaptorTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final HoldRepository repository = mock(HoldRepository.class);
    private final SeatHoldService service = new SeatHoldService(repository, FIXED_CLOCK);

    @Test
    void savedHold_expiresTenMinutesFromNow() {
        service.placeHold(7, 4);

        // The catcher, typed to what save() takes.
        ArgumentCaptor<Hold> captor = ArgumentCaptor.forClass(Hold.class);

        // capture() sits where the argument would: "the call happened, and grab what it got".
        verify(repository).save(captor.capture());

        Hold saved = captor.getValue();
        System.out.println("captured: " + saved);

        // The real rule: expiry = the fixed clock's time plus ten minutes.
        assertEquals(NOW.plus(Duration.ofMinutes(10)), saved.expiresAt());
        assertEquals(7, saved.id());
        assertEquals(4, saved.seats());
    }
}
