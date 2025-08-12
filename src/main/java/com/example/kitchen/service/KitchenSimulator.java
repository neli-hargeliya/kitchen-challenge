package com.example.kitchen.service;

import com.example.kitchen.dto.ChallengeActionDto;
import com.example.kitchen.dto.ChallengeOrderDto;
import com.example.kitchen.dto.ChallengeResultDto;
import com.example.kitchen.mapper.ActionMapper;
import com.example.kitchen.mapper.OrderMapper;
import com.example.kitchen.model.Order;
import com.example.kitchen.repository.ActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class KitchenSimulator {

    // Orchestrates place/pickup operations against in-memory storages and DB-ledger
    private final KitchenService kitchenService;

    // Read all recorded actions (PLACE/MOVE/PICKUP/DISCARD) for building the final payload
    private final ActionRepository actionRepository;

    // Maps ActionEntity -> ChallengeActionDto (converts Instant to μs)
    private final ActionMapper actionMapper;

    // Maps ChallengeOrderDto -> internal Order model
    private final OrderMapper orderMapper;

    @Value("${challenge.base-url}")
    private String baseUrl;
    @Value("${challenge.auth-token}")
    private String authToken;

    private String lastTestId;
    private final Random random = new Random();

    /**
     * Run a single simulation:
     * 1) GET /new → list of orders + `x-test-id`
     * 2) For each order (at ~ratePerSecond):
     * place → wait random delay in [minPickupSec; maxPickupSec] → pickup
     * 3) Wait `maxPickupSec + 3` seconds to ensure actions are persisted
     * 4) POST /solve with actions filtered by this run window (μs)
     */
    public Mono<Void> runSimulation(int ratePerSecond, int minPickupSec, int maxPickupSec) {
        final long runStartMicros = System.currentTimeMillis() * 1000L;

        return fetchOrdersFromServer()
                .flatMapMany(Flux::fromIterable)
                // Throttle placements to desired rate (floor to 1ms to avoid division by zero)
                .delayElements(Duration.ofMillis(Math.max(1, 1000L / Math.max(1, ratePerSecond))))
                // Keep per-order sequence: place -> delay -> pickup
                .concatMap(order -> {
                    int pickupDelay = random.nextInt(maxPickupSec - minPickupSec + 1) + minPickupSec;
                    return kitchenService.placeOrder(order)
                            .then(Mono.delay(Duration.ofSeconds(pickupDelay)))
                            .then(kitchenService.pickupOrder(order.id()));
                })
                // Grace period: let async writes land in the ledger
                .then(Mono.delay(Duration.ofSeconds(maxPickupSec + 3L)))
                // Gather & submit only actions produced during this run
                .then(Mono.defer(() -> {
                    long runEndMicros = System.currentTimeMillis() * 1000L;
                    return submitResultsToServer(ratePerSecond, minPickupSec, maxPickupSec, runStartMicros, runEndMicros);
                }))
                .doOnSuccess(v -> log.info("Simulation completed"));
    }

    /**
     * Build the challenge payload and POST it to `/solve`.
     * - Filters actions by [runStartMicros; runEndMicros]
     * - Sorts by timestamp ascending for stable output
     * - Converts rate/min/max to microseconds
     * - Sends header `x-test-id` obtained from `/new`
     */
    private Mono<Void> submitResultsToServer(int ratePerSecond, int minPickupSec, int maxPickupSec,
                                             long runStartMicros, long runEndMicros) {
        return actionRepository.findAll()
                .map(actionMapper::toChallengeActionDto)
                .filter(dto -> dto.timestamp() >= runStartMicros && dto.timestamp() <= runEndMicros)
                .sort(Comparator.comparingLong(ChallengeActionDto::timestamp))
                .collectList()
                .flatMap(actions -> {
                    var payload = new ChallengeResultDto(
                            new ChallengeResultDto.SimulationOptions(
                                    1_000_000L / Math.max(1, ratePerSecond),
                                    minPickupSec * 1_000_000L,
                                    maxPickupSec * 1_000_000L
                            ),
                            actions
                    );

                    if (lastTestId == null || lastTestId.isBlank()) {
                        return Mono.error(new IllegalStateException("Missing x-test-id from /new"));
                    }

                    WebClient wc = WebClient.builder().baseUrl(baseUrl).build();
                    return wc.post()
                            .uri(uriBuilder -> uriBuilder.path("/solve").queryParam("auth", authToken).build())
                            .header("x-test-id", lastTestId)
                            .bodyValue(payload)
                            .retrieve()
                            .bodyToMono(String.class)
                            .doOnNext(r -> log.info("Challenge server response: {}", r))
                            .then();
                });

    }

    /**
     * GET `/new?auth=...`:
     * - Captures `x-test-id` response header (case-insensitive per HTTP; Spring normalizes keys)
     * - Maps body to internal `Order` objects
     * - 10s timeout; on error logs and returns empty list
     */
    private Mono<List<Order>> fetchOrdersFromServer() {
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/new").queryParam("auth", authToken).build())
                .retrieve()
                .toEntityList(ChallengeOrderDto.class)
                .doOnNext(response -> {
                    // Header names are case-insensitive, but Spring returns canonicalized keys
                    var h = response.getHeaders().getFirst("x-test-id");
                    if (h != null && !h.isBlank()) lastTestId = h;
                })
                .map(entity -> entity.getBody() != null
                        ? entity.getBody().stream().map(orderMapper::toOrder).toList()
                        : List.<Order>of())
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.error("Failed to fetch orders from challenge API", e);
                    return Mono.just(List.of());
                });
    }
}
