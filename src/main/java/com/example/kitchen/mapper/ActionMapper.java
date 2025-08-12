package com.example.kitchen.mapper;

import com.example.kitchen.dto.ChallengeActionDto;
import com.example.kitchen.model.ActionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ActionMapper {

    /**
     * Map ledger entity -> challenge DTO.
     * Converts Instant -> epoch micros; copies orderId/action/target as-is.
     */
    @Mapping(target = "timestamp", source = "timestamp", qualifiedByName = "instantToMicros")
    @Mapping(target = "id",        source = "orderId")
    @Mapping(target = "action",    source = "action")
    @Mapping(target = "target",    source = "target")
    ChallengeActionDto toChallengeActionDto(ActionEntity entity);

    /** Instant -> epoch micros (null-safe). */
    @Named("instantToMicros")
    static long instantToMicros(Instant ts) {
        if (ts == null) return 0L;
        return ts.getEpochSecond() * 1_000_000L + ts.getNano() / 1_000L;
    }
}
