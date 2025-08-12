package com.example.kitchen.model;

import com.example.kitchen.enums.Temperature;
import lombok.With;

import java.time.Instant;

// Domain model used in services (immutable record with Lombok @With for copies)
@With
public record Order(
        String id,           // business order id (external)
        String name,         // display name
        Temperature temp,    // HOT / COLD / ROOM
        int freshness,       // freshness budget in seconds
        Instant placedAt     // when the order was placed (set by KitchenService)
) {
}
