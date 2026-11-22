package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lesson 9 / Subtask S8: strict stubbing.
 * MockitoExtension checks after each test: was every taught stub actually used?
 */
@ExtendWith(MockitoExtension.class)
class L9StrictStubbingTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private HoldRepository repository; // the extension fills this before each test

    private SeatHoldService service;

    @BeforeEach
    void buildService() {
        service = new SeatHoldService(repository, FIXED_CLOCK);
    }

    @Test
    void holdWithRoom_allowsOneMoreSeat() {
        Hold hold = new Hold(7, 4, NOW.plus(Duration.ofMinutes(10)));
        when(repository.findById(7)).thenReturn(Optional.of(hold)); // taught...

        assertTrue(service.canAddSeat(7)); // ...and used. Strict Mockito is content.
    }
}
