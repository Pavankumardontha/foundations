package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.UUID;

// Lesson 16: the WRONG way. @Data's toString() includes `seatMap`, which points back at
// the map whose toString() includes this seat -> the recursion closes here.
@Data
@Entity
@Table(name = "seat")
public class BadSeatEntity {

    @Id
    private UUID id;

    private String rowLabel;

    private int seatNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_map_id")
    private BadSeatMapEntity seatMap;
}
