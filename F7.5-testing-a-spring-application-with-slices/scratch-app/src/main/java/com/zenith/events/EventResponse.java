package com.zenith.events;

import java.time.Instant;
import java.util.UUID;

record EventResponse(
        UUID id,
        String name,
        UUID venueId,
        Instant salesOpenAt,
        Instant startsAt,
        int capacity) {
}
