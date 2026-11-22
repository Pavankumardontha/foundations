package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;

/**
 * Lesson 5: argument matchers — placeholders instead of exact values.
 * anyLong(): "any id". eq(x): "exactly x". longThat(...): "any long passing this check".
 * Rule: within one taught call, use all raw values or all matchers. Never mix.
 */
class L5MatchersTest {

    /** Demo interface with TWO parameters, to show the mixing rule. */
    interface SeatMap {
        boolean reserve(long eventId, String section);
    }

    @Test
    void anyLong_oneTeachingCoversEveryId() {
        HoldRepository repository = mock(HoldRepository.class);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        when(repository.findById(anyLong())).thenReturn(Optional.of(hold));

        System.out.println("findById(1)   -> " + repository.findById(1));
        System.out.println("findById(42)  -> " + repository.findById(42));
        System.out.println("findById(999) -> " + repository.findById(999));
        assertTrue(repository.findById(123456).isPresent());
    }

    @Test
    void longThat_teachesByCondition() {
        HoldRepository repository = mock(HoldRepository.class);
        Hold hold = new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z"));

        // Only ids above 100 "exist".
        when(repository.findById(longThat(id -> id > 100))).thenReturn(Optional.of(hold));

        System.out.println("findById(150) -> " + repository.findById(150));
        System.out.println("findById(5)   -> " + repository.findById(5));
        assertTrue(repository.findById(150).isPresent());
        assertTrue(repository.findById(5).isEmpty());
    }

    @Test
    void mixingRawValueAndMatcher_blowsUp() {
        SeatMap seatMap = mock(SeatMap.class);

        InvalidUseOfMatchersException boom = assertThrows(
                InvalidUseOfMatchersException.class,
                () -> when(seatMap.reserve(42, anyString())).thenReturn(true));

        System.out.println("--- real Mockito error message ---");
        System.out.println(boom.getMessage());
    }

    @Test
    void eq_fixesTheMix() {
        SeatMap seatMap = mock(SeatMap.class);

        when(seatMap.reserve(eq(42L), anyString())).thenReturn(true);

        assertTrue(seatMap.reserve(42, "balcony"));   // taught: event 42, any section
        assertFalse(seatMap.reserve(43, "balcony"));  // 43 was never taught -> default
    }
}
