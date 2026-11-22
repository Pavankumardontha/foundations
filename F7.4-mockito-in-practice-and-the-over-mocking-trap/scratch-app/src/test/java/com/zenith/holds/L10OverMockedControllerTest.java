package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Lesson 10 / Subtask S6: the over-mocked test.
 * Everything below the controller is a double. The real service logic,
 * the repository, and the gateway are all out of the picture.
 * This test stays green even when the real service is broken.
 */
class L10OverMockedControllerTest {

    @Test
    void placeHold_returnsConfirmation() {
        // Mockito can mock classes too, not just interfaces.
        // That power gets misused exactly here: the real placeHold never runs.
        SeatHoldService service = mock(SeatHoldService.class);

        when(service.placeHold(7, 4))
                .thenReturn(new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z")));

        HoldController controller = new HoldController(service);

        String answer = controller.placeHold(7, 4);

        assertEquals("hold 7 for 4 seats, expires 2026-11-17T10:10:00Z", answer);
        verify(service).placeHold(7, 4);
    }
}
