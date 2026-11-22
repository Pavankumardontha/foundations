package com.zenith.holds;

import org.springframework.stereotype.Service;

@Service
public class SeatHoldService {
    private final HoldRepository holds;
    private final SeatLedger ledger;

    public SeatHoldService(HoldRepository holds, SeatLedger ledger) {
        this.holds = holds;
        this.ledger = ledger;
    }

    public String hold(String seatId) {
        if (ledger.isSold(seatId)) {
            return "Seat " + seatId + " cannot be held (already sold)";
        }
        holds.save(seatId);
        ledger.markHeld(seatId);
        return "Seat " + seatId + " held (" + holds.allHeldSeats().size() + " seat(s) now on hold)";
    }
}
