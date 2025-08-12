package com.example.kitchen.mapper;
import com.example.kitchen.enums.ActionType;
import com.example.kitchen.enums.StorageType;
import com.example.kitchen.model.ActionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ActionEntityMapperTest {

    private final ActionEntityMapper mapper = new ActionEntityMapper() {}; // default method only

    @Test
    void shouldBuildActionEntity_withNowTimestampAndGivenFields() {
        // when
        Instant before = Instant.now().minusSeconds(5);
        ActionEntity e = mapper.toEntity("o-5", ActionType.MOVE, StorageType.COOLER);
        Instant after = Instant.now().plusSeconds(5);

        // then
        assertEquals("o-5", e.getOrderId());
        assertEquals(ActionType.MOVE, e.getAction());
        assertEquals(StorageType.COOLER, e.getTarget());
        assertNotNull(e.getTimestamp());

        // timestamp is "around now" (±5s window)
        assertTrue(!e.getTimestamp().isBefore(before) && !e.getTimestamp().isAfter(after),
                "timestamp should be near 'now'");
    }
}