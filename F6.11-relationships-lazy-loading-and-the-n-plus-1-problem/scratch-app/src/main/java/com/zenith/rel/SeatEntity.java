package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

// The Java class that stands for the "seat" table. One object = one row.
@Entity
@Table(name = "seat")
public class SeatEntity {

    @Id
    private UUID id;            // column: id (the primary key)

    private String rowLabel;    // column: row_label
    private int seatNumber;     // column: seat_number

    // Many seats belong to one seat map, so: @ManyToOne.
    // Lesson 7: LAZY written out. @ManyToOne defaults to EAGER - the trap -
    // so this project writes the fetch type on every relationship.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_map_id")   // the column in THIS table that holds the key
    private SeatMapEntity seatMap;

    protected SeatEntity() {}   // Hibernate builds the object empty, then fills the fields

    // Lesson 4: a constructor for NEW seats. The seat map is set separately,
    // by the helper on SeatMapEntity, so both sides stay in step.
    public SeatEntity(UUID id, String rowLabel, int seatNumber) {
        this.id = id;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
    }

    public UUID getId() { return id; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public SeatMapEntity getSeatMap() { return seatMap; }

    // Package-private on purpose: only the helper on SeatMapEntity may call it.
    void setSeatMap(SeatMapEntity seatMap) { this.seatMap = seatMap; }
}
