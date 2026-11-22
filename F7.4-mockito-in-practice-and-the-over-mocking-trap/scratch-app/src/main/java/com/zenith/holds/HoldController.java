package com.zenith.holds;

/**
 * The web layer: receives the request, asks the service, shapes the answer.
 * Kept free of Spring here; the pattern is identical with @RestController.
 */
public class HoldController {

    private final SeatHoldService service;

    public HoldController(SeatHoldService service) {
        this.service = service;
    }

    /** POST /holds — returns a short confirmation string for the client. */
    public String placeHold(long id, int seats) {
        Hold hold = service.placeHold(id, seats);
        return "hold " + hold.id() + " for " + hold.seats()
                + " seats, expires " + hold.expiresAt();
    }
}
