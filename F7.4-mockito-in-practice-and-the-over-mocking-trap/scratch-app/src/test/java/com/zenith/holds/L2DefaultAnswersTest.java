package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Lesson 2: a fresh mock answers safe defaults until you teach it,
 * and it stores nothing — a mock is not a little database.
 */
class L2DefaultAnswersTest {

    /** A small demo interface, only to watch defaults across return types. */
    interface Gadget {
        String name();
        int count();
        boolean active();
        List<String> parts();
    }

    @Test
    void freshMockAnswersSafeDefaults() {
        Gadget gadget = Mockito.mock(Gadget.class);

        System.out.println("name()   -> " + gadget.name());
        System.out.println("count()  -> " + gadget.count());
        System.out.println("active() -> " + gadget.active());
        System.out.println("parts()  -> " + gadget.parts());
    }

    @Test
    void saveStoresNothing_aMockIsNotADatabase() {
        HoldRepository repository = Mockito.mock(HoldRepository.class);

        repository.save(new Hold(7, 4, Instant.parse("2026-11-17T10:10:00Z")));

        System.out.println("findById(7) after save -> " + repository.findById(7));
        assertTrue(repository.findById(7).isEmpty()); // nothing was stored
    }

    @Test
    void serviceWithFreshMock_alwaysSaysNo() {
        HoldRepository repository = Mockito.mock(HoldRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        SeatHoldService service = new SeatHoldService(repository, fixedClock);

        // findById answers its default (empty Optional), so the service says no.
        assertFalse(service.canAddSeat(7));
    }
}
