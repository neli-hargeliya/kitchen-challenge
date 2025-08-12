package com.example.kitchen.dto;

import com.example.kitchen.enums.ActionType;
import com.example.kitchen.enums.StorageType;

import java.time.Instant;

/**
 * Action emitted to the challenge payload.
 *
 * <p>All timestamps are in <b>microseconds since Unix epoch</b>.
 * This record also provides a convenience ctor that accepts an {@link Instant}
 * and converts it to microseconds with full precision (seconds + nanos).
 */
public record ChallengeActionDto(
        long timestamp,     // event time in microseconds since epoch
        String id,          // order id
        ActionType action,  // PLACE / MOVE / PICKUP / DISCARD
        StorageType target  // storage involved (heater/cooler/shelf)
) {
    /**
     * Convenience constructor: convert {@code Instant} -> epoch microseconds.
     * Uses: micros = epochSeconds * 1_000_000 + nanos / 1_000.
     * (Fits easily into Java long; overflow is not a concern.)
     */
    public ChallengeActionDto(Instant timestamp, String id, ActionType action, StorageType target) {
        // Convert Instant to Unix time in microseconds (full precision)
        this(timestamp.getEpochSecond() * 1_000_000L + timestamp.getNano() / 1_000L, id, action, target);
    }
}
