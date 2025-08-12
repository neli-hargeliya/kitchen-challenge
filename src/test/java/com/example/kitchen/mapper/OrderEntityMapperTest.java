package com.example.kitchen.mapper;

import com.example.kitchen.enums.StorageType;
import com.example.kitchen.enums.Temperature;
import com.example.kitchen.model.Order;
import com.example.kitchen.model.OrderEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderEntityMapperTest {

    private final OrderEntityMapper mapper = Mappers.getMapper(OrderEntityMapper.class);

    @Test
    void shouldMapOrderAndStorage_toOrderEntity_andKeepPlacedAt() {
        // given
        Instant placed = Instant.ofEpochSecond(1_700_000_000L);
        Order order = new Order("id-1", "Pizza", Temperature.HOT, 90, placed);

        // when
        OrderEntity entity = mapper.toEntity(order, StorageType.HEATER);

        // then
        assertEquals("id-1", entity.getId());
        assertEquals("Pizza", entity.getName());
        assertEquals(Temperature.HOT, entity.getTemp());
        assertEquals(StorageType.HEATER, entity.getStorage());
        assertEquals(Integer.valueOf(90), entity.getFreshness());
        assertEquals(placed, entity.getPlacedAt(), "placedAt must come from Order, not Instant.now()");
    }
}
