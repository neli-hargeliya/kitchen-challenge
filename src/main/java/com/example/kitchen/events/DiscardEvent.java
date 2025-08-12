package com.example.kitchen.events;

import com.example.kitchen.enums.StorageType;
import com.example.kitchen.model.Order;

/** Emitted when an order is discarded from a storage (e.g., expired on SHELF). */
public record DiscardEvent(
        Order order,          // the affected order
        StorageType from      // storage the order was removed from
) {}