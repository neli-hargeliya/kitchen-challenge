package com.example.kitchen.events;

/**
 * Result of a removal attempt by id.
 * - removed: whether the order was actually found and removed from the storage.
 * - expired: whether the order was expired at the moment of removal (for auditing).
 */
public record RemoveResult(boolean removed, boolean expired){}