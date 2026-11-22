package com.zenith.rel;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Lesson 16: the WRONG way. @Data's toString() includes the `seats` collection, and each
// seat's @Data toString() includes `seatMap` back again -> infinite recursion.
@Data
@Entity
@Table(name = "seat_map")
public class BadSeatMapEntity {

    @Id
    private UUID id;

    private String name;

    @OneToMany(mappedBy = "seatMap", fetch = FetchType.LAZY)
    private List<BadSeatEntity> seats = new ArrayList<>();
}
