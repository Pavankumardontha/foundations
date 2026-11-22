package com.zenith.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

// Lesson 5: Mockito writes the test double for you. No Spring here —
// the same plain-JUnit world as Lesson 1, minus the hand-written stub.
class L5MockitoBasicsTest {

    @Test
    void scriptedToThrow_lesson1TestRewritten() {
        EventService stub = mock(EventService.class);
        when(stub.byId(any())).thenThrow(new EventNotFoundException());

        EventController controller = new EventController(stub);

        assertThrows(EventNotFoundException.class,
                () -> controller.byId(UUID.randomUUID()));
    }

    @Test
    void scriptedToReturn_andDefaultsForEverythingElse() {
        UUID knownId = UUID.randomUUID();
        EventResponse canned = new EventResponse(knownId, "Jazz Night",
                UUID.randomUUID(),
                Instant.parse("2027-01-01T10:00:00Z"),
                Instant.parse("2027-02-01T19:00:00Z"), 300);

        EventService stub = mock(EventService.class);
        when(stub.byId(knownId)).thenReturn(canned);   // script ONE exact id

        assertEquals("Jazz Night", stub.byId(knownId).name()); // the scripted answer
        assertNull(stub.byId(UUID.randomUUID()));              // unscripted: default null
    }
}
