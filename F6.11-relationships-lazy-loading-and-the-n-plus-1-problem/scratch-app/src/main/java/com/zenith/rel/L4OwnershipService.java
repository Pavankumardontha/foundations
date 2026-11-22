package com.zenith.rel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Lesson 4: three small experiments, each in its own transaction.
// The runner calls these from outside, so every call goes through the
// transaction proxy and gets a real commit at the end.
@Service
public class L4OwnershipService {

    private static final UUID RIVERSIDE_FLOOR =
            UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");

    private final SeatMapRepository seatMaps;
    private final SeatRepository seats;

    L4OwnershipService(SeatMapRepository seatMaps, SeatRepository seats) {
        this.seatMaps = seatMaps;
        this.seats = seats;
    }

    // Remove the demo seats (row C) so the demo can run again and again.
    @Transactional
    public void cleanUpRowC() {
        seats.deleteAll(seats.findByRowLabel("C"));
    }

    // Experiment 1 - BREAK IT: change only the mirror list. The owning side
    // (seat.seatMap) stays null. The transaction commits when this method returns.
    @Transactional
    public void addToListOnly() {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        SeatEntity c1 = new SeatEntity(UUID.randomUUID(), "C", 1);
        map.getSeats().add(c1);                 // mirror side only!
        System.out.println(">>> [list-only]   in-memory list size now: "
                + map.getSeats().size());
    }

    // Experiment 2 - set ONLY the owning side. The list is never touched.
    @Transactional
    public void setOwningSideOnly() {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        SeatEntity c2 = new SeatEntity(UUID.randomUUID(), "C", 2);
        c2.setSeatMap(map);                     // owning side: the foreign key
        seats.save(c2);                         // let Hibernate manage the new object
        System.out.println(">>> [owning-only] saved seat C2; the list was never touched");
    }

    // Experiment 3 - THE FIX: the helper sets both sides in one call.
    @Transactional
    public void addBothSides() {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        SeatEntity c3 = new SeatEntity(UUID.randomUUID(), "C", 3);
        map.addSeat(c3);                        // helper: list AND owning side
        seats.save(c3);
        System.out.println(">>> [helper]      in-memory list already correct: "
                + map.getSeats().size() + " seats");
    }

    // Fresh look: a new transaction, a fresh load, and the real row count.
    @Transactional(readOnly = true)
    public void report(String label) {
        SeatMapEntity map = seatMaps.findById(RIVERSIDE_FLOOR).orElseThrow();
        System.out.println(">>> [" + label + "] rows in seat table: " + seats.count()
                + " | fresh list size: " + map.getSeats().size());
    }
}
