package com.example.kitchen.mapper;

import com.example.kitchen.dto.ChallengeActionDto;
import com.example.kitchen.enums.ActionType;
import com.example.kitchen.enums.StorageType;
import com.example.kitchen.model.ActionEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionMapperTest {
    private final ActionMapper mapper = Mappers.getMapper(ActionMapper.class);

    @Test
    void shouldMapEntityToDto_andConvertInstantToMicros() {
        // given
        Instant ts = Instant.ofEpochSecond(1_600_000_000L, 123_456_000L); // nanos=123,456,000 -> +123,456 μs
        ActionEntity e = new ActionEntity();
        e.setTimestamp(ts);
        e.setOrderId("o-1");
        e.setAction(ActionType.PLACE);
        e.setTarget(StorageType.SHELF);

        // when
        ChallengeActionDto dto = mapper.toChallengeActionDto(e);

        // then
        long expectedMicros = 1_600_000_000L * 1_000_000L + 123_456L;
        assertEquals(expectedMicros, dto.timestamp());
        assertEquals("o-1", dto.id());
        assertEquals(ActionType.PLACE, dto.action());
        assertEquals(StorageType.SHELF, dto.target());
    }

    @Test
    void shouldReturnZeroTimestamp_whenEntityTimestampIsNull() {
        ActionEntity e = new ActionEntity();
        e.setTimestamp(null);
        e.setOrderId("o-2");
        e.setAction(ActionType.DISCARD);
        e.setTarget(StorageType.COOLER);

        ChallengeActionDto dto = mapper.toChallengeActionDto(e);

        assertEquals(0L, dto.timestamp());
        assertEquals("o-2", dto.id());
        assertEquals(ActionType.DISCARD, dto.action());
        assertEquals(StorageType.COOLER, dto.target());
    }
}