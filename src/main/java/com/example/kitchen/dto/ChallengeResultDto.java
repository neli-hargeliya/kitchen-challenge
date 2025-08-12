package com.example.kitchen.dto;

import java.util.List;

/**
 * Payload sent to the Challenge `/solve` endpoint.
 * <p>
 * Notes:
 * - All time-related fields are in **microseconds**.
 * - `actions` should be the chronological list produced during the run
 * (each action timestamp is epoch microseconds).
 */
public record ChallengeResultDto(
        SimulationOptions options,       // simulation timing options
        List<ChallengeActionDto> actions // ordered actions captured for this run
) {
    /**
     * Timing options for the simulation.
     * <p>
     * Semantics (all in microseconds):
     * - rate — interval between order placements (ms per order), i.e. inverse throughput.
     * - min  — minimum pickup delay (inclusive).
     * - max  — maximum pickup delay (inclusive).
     */
    public record SimulationOptions(
            long rate,
            long min,
            long max
    ) {
    }
}