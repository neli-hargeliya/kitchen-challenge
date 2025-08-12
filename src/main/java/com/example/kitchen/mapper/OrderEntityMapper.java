package com.example.kitchen.mapper;

import com.example.kitchen.enums.StorageType;
import com.example.kitchen.model.Order;
import com.example.kitchen.model.OrderEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderEntityMapper {

    /**
     * Persistable snapshot of Order in a specific storage.
     * Uses placedAt from the Order (already set in KitchenService).
     */
    @Mapping(target = "id",        source = "order.id")
    @Mapping(target = "name",      source = "order.name")
    @Mapping(target = "temp",      source = "order.temp")
    @Mapping(target = "freshness", source = "order.freshness")
    @Mapping(target = "storage",   source = "storageType")
    @Mapping(target = "placedAt",  source = "order.placedAt")
    OrderEntity toEntity(Order order, StorageType storageType);
}