package com.example.kitchen.service;

import com.example.kitchen.enums.ActionType;
import com.example.kitchen.enums.StorageType;
import com.example.kitchen.events.DiscardEvent;
import com.example.kitchen.events.MoveEvent;
import com.example.kitchen.mapper.ActionEntityMapper;
import com.example.kitchen.mapper.OrderEntityMapper;
import com.example.kitchen.model.Order;
import com.example.kitchen.model.OrderEntity;
import com.example.kitchen.repository.ActionRepository;
import com.example.kitchen.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * Orchestrates order placement/movement/pickup and writes a persistent action ledger.
 * Storage decisions are delegated to StorageService; DB writes go via R2DBC repositories/templates.
 * All public flows are wrapped in a reactive transaction (TransactionalOperator).
 */
public class KitchenService {

    private final StorageService storageService;
    private final OrderRepository orderRepository;
    private final ActionRepository actionRepository;
    private final OrderEntityMapper orderEntityMapper;
    private final ActionEntityMapper actionEntityMapper;
    private final R2dbcEntityTemplate template;
    private final TransactionalOperator tx;

    /**
     * Place an order:
     * 1) try ideal storage; if full → try SHELF;
     * 2) if SHELF is full → try moving one from SHELF to its ideal place;
     * 3) if nothing moved → discard soonest-to-expire from SHELF, then place on SHELF.
     * Always log and write a PLACE action on success.
     */
    @Transactional
    public Mono<Void> placeOrder(Order order) {
        Order withTs = order.withPlacedAt(Instant.now());

        StorageType ideal = storageService.idealFor(order.temp());

        return storageService.tryAddOrder(ideal, withTs)
                .flatMap(placedIdeal -> {
                    if (placedIdeal) {
                        return savePlace(withTs, ideal);
                    }
                    return storageService.tryAddOrder(StorageType.SHELF, withTs)
                            .flatMap(placedShelf -> {
                                if (placedShelf) return savePlace(withTs, StorageType.SHELF);
                                return storageService.tryMoveOneFromShelf()
                                        .flatMap(move -> {
                                            return persistMove(move)
                                                    .then(storageService.tryAddOrder(StorageType.SHELF, withTs)
                                                            .flatMap(ok -> ok ? savePlace(withTs, StorageType.SHELF)
                                                                    : Mono.error(new IllegalStateException("Shelf capacity race"))));
                                        })
                                        .switchIfEmpty(
                                                storageService.discardMinFromShelf()
                                                        .flatMap(discard -> persistDiscard(discard))
                                                        .then(storageService.tryAddOrder(StorageType.SHELF, withTs)
                                                                .flatMap(ok -> ok ? savePlace(withTs, StorageType.SHELF)
                                                                        : Mono.error(new IllegalStateException("Shelf capacity race after discard"))))
                                        );
                            });
                })
                .as(tx::transactional);
    }

    /**
     * Insert OrderEntity + write PLACE action.
     */
    private Mono<Void> savePlace(Order order, StorageType target) {
        var entity = orderEntityMapper.toEntity(order, target);
        log.info("place id={} -> {}", order.id(), target);
        return template.insert(OrderEntity.class)
                .using(entity)
                .then(actionRepository.save(
                        actionEntityMapper.toEntity(order.id(), ActionType.PLACE, target)))
                .then();
    }

    /**
     * Persist a MOVE: update OrderEntity.storage, write MOVE action.
     */
    private Mono<Void> persistMove(MoveEvent move) {
        log.info("move id={} {} -> {}", move.order().id(), move.from(), move.to());
        return orderRepository.findById(move.order().id())
                .flatMap(e -> {
                    e.setStorage(move.to());
                    return orderRepository.save(e);
                })
                .then(actionRepository.save(actionEntityMapper.toEntity(move.order().id(), ActionType.MOVE, move.to())))
                .then();
    }

    /**
     * Persist a DISCARD: write DISCARD action then delete OrderEntity.
     * Errors while deleting are swallowed to keep the stream moving.
     */
    private Mono<Void> persistDiscard(DiscardEvent ev) {
        log.info("discard id={} from {}", ev.order().id(), ev.from());
        return actionRepository.save(actionEntityMapper.toEntity(ev.order().id(), ActionType.DISCARD, ev.from()))
                .then(orderRepository.deleteById(ev.order().id()))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    /**
     * Pickup by id:
     * - read OrderEntity to locate storage
     * - remove from StorageService and get "expired at removal" flag
     * - write PICKUP or DISCARD action accordingly, then delete OrderEntity
     * Missing order (either in DB or in storage) is treated as a no-op with a log line.
     */
    public Mono<Void> pickupOrder(String orderId) {
        return orderRepository.findById(orderId)
                .flatMap(entity -> {
                    StorageType where = entity.getStorage();

                    // Remove and get "expired at removal" from StorageService
                    return storageService.removeByIdWithExpiry(where, orderId)
                            .flatMap(res -> {
                                if (!res.removed()) {
                                    log.info("pickup: order {} not found, ignore", orderId);
                                    return Mono.empty();
                                }
                                ActionType act = res.expired() ? ActionType.DISCARD : ActionType.PICKUP;
                                return actionRepository
                                        .save(actionEntityMapper.toEntity(orderId, act, where))
                                        .then(orderRepository.deleteById(orderId));
                            })
                            .onErrorResume(ex -> {
                                // If storage removal failed unexpectedly, do not write action
                                log.error("pickup error for id={}", orderId, ex);
                                return Mono.empty();
                            });
                })
                .switchIfEmpty(Mono.fromRunnable(() -> log.info("pickup: order {} not found in DB, ignore", orderId)))
                .then()
                .as(tx::transactional);
    }
}
