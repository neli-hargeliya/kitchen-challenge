package com.example.kitchen.model;

import com.example.kitchen.enums.ActionType;
import com.example.kitchen.enums.StorageType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

// Actions ledger row (Spring Data R2DBC entity)
@Data
@Table(name = "actions")
public class ActionEntity {
    @Id
    private String id;          // primary key (string-based; provided by app, not auto-generated)
    @Column("ts")
    private Instant timestamp;  // event time (UTC). Column name is "ts" in DB
    private String orderId;     // business order id
    private ActionType action;  // PLACE / MOVE / PICKUP / DISCARD
    private StorageType target; // storage affected: HEATER / COOLER / SHELF
}
