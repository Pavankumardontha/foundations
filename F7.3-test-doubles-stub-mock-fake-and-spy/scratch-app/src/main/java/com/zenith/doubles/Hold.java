package com.zenith.doubles;

/**
 * A seat hold: a customer is holding some seats for an event,
 * but has not paid yet. A record is a small class that only carries data.
 */
public record Hold(long id, int seats) {
}
