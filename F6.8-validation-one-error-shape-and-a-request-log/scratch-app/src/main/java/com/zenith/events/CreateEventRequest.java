package com.zenith.events;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@SalesBeforeStart
record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull UUID venueId,
        @NotNull @Future Instant salesOpenAt,
        @NotNull @Future Instant startsAt,
        @Positive int capacity) {
}
