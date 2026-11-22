package com.zenith.rel;

import java.time.OffsetDateTime;
import java.util.UUID;

// Lesson 12: the response shape - a record, built inside the service transaction.
// Plain data only: no entities, no proxies, nothing that can touch the database.
public record EventSummary(UUID id, String name, OffsetDateTime startsAt, String venueName) {
}
