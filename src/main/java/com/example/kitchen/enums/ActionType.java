package com.example.kitchen.enums;

/**
 * Ledger action type.
 * Serialized to JSON as lowercase (e.g., "place", "move", ...).
 */
public enum ActionType {
    PLACE,   // order placed into a storage
    MOVE,    // order moved between storages
    PICKUP,  // order picked up (still fresh)
    DISCARD; // order discarded (expired)

    @com.fasterxml.jackson.annotation.JsonValue
    public String json() {
        // Emit lowercase token in API payloads
        return name().toLowerCase();
    }
}
