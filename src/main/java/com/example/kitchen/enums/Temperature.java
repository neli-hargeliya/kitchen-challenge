package com.example.kitchen.enums;

/**
 * Order temperature (drives ideal storage and decay rate).
 * - HOT   → ideal: HEATER
 * - COLD  → ideal: COOLER
 * - ROOM  → ideal: SHELF
 */
public enum Temperature {
    HOT, COLD, ROOM
}
