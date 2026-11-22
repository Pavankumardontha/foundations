package com.zenith.holds;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SeatLedger {
    private final Set<String> sold = new HashSet<>();
    private final Set<String> held = new HashSet<>();

    public void markSold(String seatId) {
        sold.add(seatId);
    }

    public boolean isSold(String seatId) {
        return sold.contains(seatId);
    }

    public void markHeld(String seatId) {
        held.add(seatId);
    }

    public boolean isHeld(String seatId) {
        return held.contains(seatId);
    }
}
