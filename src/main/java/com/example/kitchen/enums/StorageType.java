package com.example.kitchen.enums;

/**
 * Storage location type.
 * Serialized to JSON as lowercase (e.g., "heater", "cooler", "shelf").
 */
public enum StorageType {
    HEATER, // ideal for HOT
    COOLER, // ideal for COLD
    SHELF;  // ideal for ROOM; overflow area for others

    @com.fasterxml.jackson.annotation.JsonValue
    public String json() {
        // Emit lowercase token in API payloads
        return name().toLowerCase();
    }
}
