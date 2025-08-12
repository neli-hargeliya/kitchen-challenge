package com.example.kitchen.events;

import com.example.kitchen.enums.StorageType;
import com.example.kitchen.model.Order;

/** Emitted when an order is moved between storages (e.g., SHELF → COOLER). */
public record MoveEvent(
        Order order,          // the affected order
        StorageType from,     // source storage
        StorageType to        // destination storage
) {}

