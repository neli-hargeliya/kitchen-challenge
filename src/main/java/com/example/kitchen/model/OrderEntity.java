package com.example.kitchen.model;

import com.example.kitchen.enums.StorageType;
import com.example.kitchen.enums.Temperature;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

// Persisted snapshot of an Order (R2DBC entity)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("orders")
public class OrderEntity {
    @Id
    private String id;            // use provided order id — do NOT auto-generate
    private String name;
    private Temperature temp;     // stored as VARCHAR/TEXT
    private StorageType storage;  // current storage location (VARCHAR/TEXT)
    private Integer freshness;    // seconds (same semantics as domain)
    @Column("placed_at")
    private Instant placedAt;     // absolute UTC timestamp when the order was placed
}
