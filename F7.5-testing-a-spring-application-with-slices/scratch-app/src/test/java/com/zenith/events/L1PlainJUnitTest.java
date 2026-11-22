package com.zenith.events;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

// Lesson 1: a plain JUnit test, no Spring anywhere.
// It can prove "byId throws EventNotFoundException".
// It CANNOT prove "the API answers 404 with our error JSON" —
// no status code and no JSON exist in this world.
class L1PlainJUnitTest {

    @Test
    void byIdThrowsWhenEventDoesNotExist() {
        // A hand-written stub (the F7.3 idea): a stand-in EventService
        // whose byId always throws. The repository is never touched,
        // so passing null is safe here.
        EventService stub = new EventService(null) {
            @Override
            EventResponse byId(UUID id) {
                throw new EventNotFoundException();
            }
        };
        EventController controller = new EventController(stub);

        assertThrows(EventNotFoundException.class,
                () -> controller.byId(UUID.randomUUID()));
    }
}
