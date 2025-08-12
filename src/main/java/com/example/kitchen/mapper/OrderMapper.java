package com.example.kitchen.mapper;

import com.example.kitchen.dto.ChallengeOrderDto;
import com.example.kitchen.model.Order;
import com.example.kitchen.enums.Temperature;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    /**
     * ChallengeOrderDto("temperature" as String) -> internal Order (enum).
     * placedAt is set later in KitchenService.
     */
    @Mapping(target = "temp",     source = "temperature")
    @Mapping(target = "placedAt", ignore = true)
    Order toOrder(ChallengeOrderDto dto);

    /** Robust String -> Temperature converter. */
    default Temperature mapTemperature(String v) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty()) return null;
        return Temperature.valueOf(v.toUpperCase());
    }
}
