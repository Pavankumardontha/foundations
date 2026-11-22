package com.zenith.holds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatHoldServiceTest {

    static class FakeHoldRepository implements HoldRepository {
        private final List<String> held = new ArrayList<>();

        @Override
        public void save(String seatId) {
            held.add(seatId);
        }

        @Override
        public List<String> allHeldSeats() {
            return List.copyOf(held);
        }
    }

    @Test
    void holdingSeatsReportsTheRisingCount() {
        SeatHoldService service = new SeatHoldService(new FakeHoldRepository(), new SeatLedger());

        assertEquals("Seat A1 held (1 seat(s) now on hold)", service.hold("A1"));
        assertEquals("Seat A2 held (2 seat(s) now on hold)", service.hold("A2"));
    }
}
