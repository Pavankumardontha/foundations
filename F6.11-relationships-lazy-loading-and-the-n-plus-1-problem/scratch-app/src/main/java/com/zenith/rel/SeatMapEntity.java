package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// The Java class that stands for the "seat_map" table. One object = one row.
@Entity
@Table(name = "seat_map")
public class SeatMapEntity {

    @Id
    private UUID id;        // column: id (the primary key)

    private String name;    // column: name

    // Many seat maps belong to one venue, so: @ManyToOne.
    // Lesson 7: LAZY written out. @ManyToOne defaults to EAGER - the trap -
    // so this project writes the fetch type on every relationship.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")   // the column in THIS table that holds the key
    private VenueEntity venue;

    // Lesson 3: the mirror direction. One seat map has many seats.
    // mappedBy = "seatMap" names the FIELD on SeatEntity that owns the foreign key.
    // No new column anywhere: Hibernate fills this list by reading seat.seat_map_id.
    // Lesson 7: LAZY is already the collection default; written out anyway (project rule).
    @OneToMany(mappedBy = "seatMap", fetch = FetchType.LAZY)
    private List<SeatEntity> seats = new ArrayList<>();

    protected SeatMapEntity() {}     // Hibernate builds the object empty, then fills the fields

    public UUID getId() { return id; }
    public String getName() { return name; }
    public VenueEntity getVenue() { return venue; }
    public List<SeatEntity> getSeats() { return seats; }

    // Lesson 4: the helper that keeps both sides in step.
    // The owning side (seat.seatMap) is what the database records;
    // the mirror list gets the same truth for in-memory readers.
    public void addSeat(SeatEntity seat) {
        seats.add(seat);
        seat.setSeatMap(this);
    }
}
