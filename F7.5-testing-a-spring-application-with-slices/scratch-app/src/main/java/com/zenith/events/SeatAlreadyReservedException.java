package com.zenith.events;

// Thrown when a reservation clashes with the current state: someone holds the seat.
class SeatAlreadyReservedException extends DomainException {

    SeatAlreadyReservedException(String seatLabel) {
        super("seat_already_reserved", "Seat " + seatLabel + " is already reserved.");
    }
}
