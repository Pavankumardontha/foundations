package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Lesson 11 / Subtasks S7 + S9: the same scenarios, with the in-memory fake.
 * No stubbing, no verifying. Real logic runs against real (in-memory) behaviour,
 * and the test asserts on outcomes. Removing the save makes these go red.
 */
class L11FakeVsMockTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeHoldRepository repository = new FakeHoldRepository();
    private final SeatHoldService service = new SeatHoldService(repository, FIXED_CLOCK);

    @Test
    void placedHold_canTakeAnotherSeat() {
        service.placeHold(7, 4);

        // The whole use case through real logic and real storage behaviour:
        // placeHold stored it, canAddSeat finds it, clock says not expired, 4 < 10.
        assertTrue(service.canAddSeat(7));
    }

    @Test
    void controllerWithRealServiceAndFake_provesTheUseCase() {
        // The S9 rewrite: same controller as the over-mocked test,
        // but the real service and the fake sit underneath.
        HoldController controller = new HoldController(service);

        String answer = controller.placeHold(7, 4);

        assertEquals("hold 7 for 4 seats, expires 2026-11-17T10:10:00Z", answer);
        assertTrue(repository.findById(7).isPresent()); // genuinely stored
    }
}
