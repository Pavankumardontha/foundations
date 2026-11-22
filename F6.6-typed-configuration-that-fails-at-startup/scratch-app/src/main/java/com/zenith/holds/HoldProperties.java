package com.zenith.holds;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The whole zenith.hold group as one typed, immutable, validated object.
 * A broken rule stops the application at startup.
 *
 * @param ttl              how long a seat hold lasts before it expires
 * @param maxSeatsPerOrder most seats one order may hold at once
 */
@Validated
@ConfigurationProperties(prefix = "zenith.hold")
record HoldProperties(
        @NotNull Duration ttl,
        @Positive @Max(10) int maxSeatsPerOrder) {
}
