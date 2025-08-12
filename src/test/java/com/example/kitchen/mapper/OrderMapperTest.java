package com.example.kitchen.mapper;

import com.example.kitchen.dto.ChallengeOrderDto;
import com.example.kitchen.enums.Temperature;
import com.example.kitchen.model.Order;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.*;

class OrderMapperTest {

    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void shouldMapDtoToOrder_andConvertTemperatureCaseInsensitively() {
        // given: lowercase + spaces — should still map to HOT
        ChallengeOrderDto dto = new ChallengeOrderDto(
                "o-3", "Soup", mapper.mapTemperature(" hot "), 45
        );

        // when
        Order order = mapper.toOrder(dto);

        // then
        assertEquals("o-3", order.id());
        assertEquals("Soup", order.name());
        assertEquals(Temperature.HOT, order.temp());
        assertEquals(45, order.freshness());
        assertNull(order.placedAt(), "placedAt is set later in KitchenService");
    }

    @Test
    void shouldAllowNullTemperature_resultHasNullEnum() {
        ChallengeOrderDto dto = new ChallengeOrderDto("o-4", "Water", null, 10);

        Order order = mapper.toOrder(dto);

        assertNull(order.temp());
        assertEquals(10, order.freshness());
    }
}
