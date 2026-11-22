package com.zenith.holds;

import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final SeatLedger ledger;

    public OrderService(SeatLedger ledger) {
        this.ledger = ledger;
    }

    public String purchase(String seatId) {
        if (ledger.isHeld(seatId)) {
            return "Cannot sell seat " + seatId + " (currently held)";
        }
        ledger.markSold(seatId);
        return "Order placed for seat " + seatId;
    }
}
