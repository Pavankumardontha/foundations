package com.zenith.scopes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SeatHoldServiceTest {

    @Test
    void nineMinutesIn_holdStillGood() {
        Instant heldAt = Instant.parse("2026-10-31T10:00:00Z");
        Clock frozenAt10_09 = Clock.fixed(Instant.parse("2026-10-31T10:09:00Z"), ZoneOffset.UTC);

        SeatHoldService service = new SeatHoldService(frozenAt10_09);

        assertFalse(service.isExpired(heldAt));
    }

    @Test
    void elevenMinutesIn_holdExpired() {
        Instant heldAt = Instant.parse("2026-10-31T10:00:00Z");
        Clock frozenAt10_11 = Clock.fixed(Instant.parse("2026-10-31T10:11:00Z"), ZoneOffset.UTC);

        SeatHoldService service = new SeatHoldService(frozenAt10_11);

        assertTrue(service.isExpired(heldAt));
    }
}
