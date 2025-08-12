package com.example.kitchen.dto;

import com.example.kitchen.enums.Temperature;

/**
 * DTO for orders coming from the Challenge API.
 * <p>
 * Notes:
 * - JSON field is "temp" but we bind it to the Java field "temperature".
 * - "temperature" is validated against the Temperature enum (HOT/COLD/ROOM).
 * - "freshness" is in seconds.
 */
public record ChallengeOrderDto(
        String id,                 // order id (as provided by the API)
        String name,               // display name
        @com.fasterxml.jackson.annotation.JsonProperty("temp")
        Temperature temperature,  // direct enum binding (HOT/COLD/ROOM)
        int freshness              // freshness budget in seconds
) {
}
