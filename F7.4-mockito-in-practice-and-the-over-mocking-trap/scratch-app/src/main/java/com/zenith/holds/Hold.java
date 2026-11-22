package com.zenith.holds;

import java.time.Instant;

/**
 * A seat hold: a customer is holding some seats for an event, but has not paid yet.
 * expiresAt is the moment the hold stops being valid.
 * A record is a small class that only carries data.
 */
public record Hold(long id, int seats, Instant expiresAt) {
}
