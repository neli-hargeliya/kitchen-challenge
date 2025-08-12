package com.example.kitchen.mapper;

import com.example.kitchen.enums.ActionType;
import com.example.kitchen.enums.StorageType;
import com.example.kitchen.model.ActionEntity;
import org.mapstruct.Mapper;

import java.time.Instant;

@Mapper(componentModel = "spring", imports = Instant.class)
public interface ActionEntityMapper {
    /**
     * Build ActionEntity for ledger with current timestamp.
     * Timestamp is Instant (ISO), conversion to μs happens later in ActionMapper.
     */
    default ActionEntity toEntity(String orderId, ActionType action, StorageType target) {
        ActionEntity e = new ActionEntity();
        e.setOrderId(orderId);
        e.setAction(action);
        e.setTarget(target);
        e.setTimestamp(Instant.now());
        return e;
    }
}
