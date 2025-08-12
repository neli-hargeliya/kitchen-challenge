package com.example.kitchen.service;

import com.example.kitchen.enums.StorageType;
import com.example.kitchen.enums.Temperature;
import com.example.kitchen.events.DiscardEvent;
import com.example.kitchen.events.MoveEvent;
import com.example.kitchen.events.RemoveResult;
import com.example.kitchen.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class StorageService {
    private final Map<StorageType, ConcurrentLinkedDeque<Order>> storages = new ConcurrentHashMap<>();
    private final Map<StorageType, ReentrantLock> locks = Map.of(
            StorageType.HEATER, new ReentrantLock(),
            StorageType.COOLER, new ReentrantLock(),
            StorageType.SHELF, new ReentrantLock()
    );

    private static final class ShelfEntry {
        final Order order;
        final long expiryEpochMicros;

        ShelfEntry(Order o, long expiry) {
            this.order = o;
            this.expiryEpochMicros = expiry;
        }
    }

    private final PriorityBlockingQueue<ShelfEntry> shelfHeap =
            new PriorityBlockingQueue<>(16, Comparator.comparingLong(e -> e.expiryEpochMicros));

    public StorageService() {
        storages.put(StorageType.HEATER, new ConcurrentLinkedDeque<>());
        storages.put(StorageType.COOLER, new ConcurrentLinkedDeque<>());
        storages.put(StorageType.SHELF, new ConcurrentLinkedDeque<>());
    }

    public int getMaxCapacity(StorageType type) {
        return switch (type) {
            case HEATER, COOLER -> 6;
            case SHELF -> 12;
        };
    }

    public Mono<Boolean> tryAddOrder(StorageType type, Order order) {
        return Mono.fromCallable(() -> {
            ReentrantLock lock = locks.get(type);
            lock.lock();
            try {
                initDecayIfAbsent(order);
                long now = nowMicros();
                // apply elapsed on *current* (target) storage only after we actually add it
                var dq = storages.get(type);
                if (dq.size() >= getMaxCapacity(type)) return false;

                dq.add(order);

                // Now that order is on "type", apply elapsed since last touch with the rate of "type"
                applyElapsed(order.id(), order.temp(), type, now);

                // Maintain shelf heap index
                if (type == StorageType.SHELF) {
                    long expiry = predictShelfExpiryMicros(order.id(), order.temp(), now);
                    shelfHeap.add(new ShelfEntry(order, expiry));
                }
                return true;
            } finally {
                lock.unlock();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<MoveEvent> tryMoveOneFromShelf() {
        return Mono.fromCallable(() -> {
                    var shelfLock = locks.get(StorageType.SHELF);
                    shelfLock.lock();
                    try {
                        for (Order o : storages.get(StorageType.SHELF)) {
                            StorageType ideal = idealFor(o.temp());
                            var toLock = locks.get(ideal);
                            if (toLock.tryLock()) {
                                try {
                                    var toQ = storages.get(ideal);
                                    if (toQ.size() >= getMaxCapacity(ideal)) continue;

                                    // Remove from SHELF
                                    boolean removed = storages.get(StorageType.SHELF).remove(o);
                                    if (!removed) continue;

                                    // Update decay as it *was on SHELF* until now
                                    long now = nowMicros();
                                    applyElapsed(o.id(), o.temp(), StorageType.SHELF, now);

                                    // Remove heap index
                                    shelfHeap.removeIf(e -> e.order.id().equals(o.id()));

                                    // Add to ideal queue, then apply rate of ideal from now on
                                    toQ.add(o);
                                    applyElapsed(o.id(), o.temp(), ideal, now);

                                    return new MoveEvent(o, StorageType.SHELF, ideal);
                                } finally {
                                    toLock.unlock();
                                }
                            }
                        }
                        return null;
                    } finally {
                        shelfLock.unlock();
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(ev -> ev == null ? Mono.empty() : Mono.just(ev));
    }

    public Mono<DiscardEvent> discardMinFromShelf() {
        return Mono.fromCallable(() -> {
                    var lock = locks.get(StorageType.SHELF);
                    lock.lock();
                    try {
                        ShelfEntry e = shelfHeap.poll();
                        if (e == null) return null;
                        boolean removed = storages.get(StorageType.SHELF).remove(e.order);
                        if (!removed) return null;

                        // Apply elapsed on shelf until now and mark as expired
                        long now = nowMicros();
                        applyElapsed(e.order.id(), e.order.temp(), StorageType.SHELF, now);

                        return new DiscardEvent(e.order, StorageType.SHELF);
                    } finally {
                        lock.unlock();
                    }
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(ev -> ev == null ? Mono.empty() : Mono.just(ev));
    }

    public Mono<RemoveResult> removeByIdWithExpiry(StorageType type, String orderId) {
        return Mono.fromCallable(() -> {
            var lock = locks.get(type);
            lock.lock();
            try {
                var it = storages.get(type).iterator();
                while (it.hasNext()) {
                    Order o = it.next();
                    if (o.id().equals(orderId)) {
                        // Update decay as it was on "type" until now
                        long now = nowMicros();
                        applyElapsed(orderId, o.temp(), type, now);

                        it.remove();
                        if (type == StorageType.SHELF) {
                            shelfHeap.removeIf(e -> e.order.id().equals(orderId));
                        }
                        boolean expired = isExpiredNow(orderId);
                        // Optionally, cleanup runtime state to avoid leaks
                        decays.remove(orderId);
                        return new RemoveResult(true, expired);
                    }
                }
                return new RemoveResult(false, false);
            } finally {
                lock.unlock();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public StorageType idealFor(Temperature temp) {
        return switch (temp) {
            case HOT -> StorageType.HEATER;
            case COLD -> StorageType.COOLER;
            case ROOM -> StorageType.SHELF;
        };
    }

    // ---- Freshness runtime tracking ----
    private static final class Decay {
        // Remaining freshness budget in microseconds
        long remainingMicros;
        // Last time we updated the budget (epoch micros)
        long lastUpdateMicros;

        Decay(long remainingMicros, long lastUpdateMicros) {
            this.remainingMicros = remainingMicros;
            this.lastUpdateMicros = lastUpdateMicros;
        }
    }// orderId -> decay state (lifecycle lives only during current process run)

    private final ConcurrentHashMap<String, Decay> decays = new ConcurrentHashMap<>();

    private static long nowMicros() {
        return System.currentTimeMillis() * 1000L;
    }

    private int decayRateFor(Temperature temp, StorageType where) {
        // On ideal storage decay factor = 1, otherwise 2 (twice as fast)
        boolean ideal = (temp == Temperature.HOT && where == StorageType.HEATER)
                || (temp == Temperature.COLD && where == StorageType.COOLER)
                || (temp == Temperature.ROOM && where == StorageType.SHELF);
        return ideal ? 1 : 2;
    }

    /**
     * Initialize decay on first placement (at "placedAt").
     */
    private void initDecayIfAbsent(Order o) {
        decays.computeIfAbsent(o.id(), id -> {
            long placedMicros = o.placedAt().toEpochMilli() * 1000L;
            long remaining = o.freshness() * 1_000_000L; // seconds -> micros
            return new Decay(remaining, placedMicros);
        });
    }

    /**
     * Apply elapsed time since last update according to *current* location rate.
     */
    private void applyElapsed(String orderId, Temperature temp, StorageType where, long now) {
        Decay d = decays.get(orderId);
        if (d == null) return;
        long elapsed = Math.max(0, now - d.lastUpdateMicros);
        if (elapsed == 0) {
            d.lastUpdateMicros = now;
            return;
        }
        int rate = decayRateFor(temp, where);
        // Decrease remaining budget by elapsed * rate
        d.remainingMicros -= elapsed * rate;
        d.lastUpdateMicros = now;
    }

    /**
     * Predict absolute expiry instant on SHELF if the order stays there.
     */
    private long predictShelfExpiryMicros(String orderId, Temperature temp, long now) {
        Decay d = decays.get(orderId);
        if (d == null) return now; // failsafe
        int shelfRate = decayRateFor(temp, StorageType.SHELF);
        long remainingWhileOnShelf = d.remainingMicros;
        // If we moved to shelf right now, its rate applies for future time.
        long dt = (shelfRate <= 0) ? Long.MAX_VALUE : Math.max(0, remainingWhileOnShelf) / shelfRate;
        return now + dt;
    }

    /**
     * Read-only check: has the order expired *now* on the given storage.
     */
    private boolean isExpiredNow(String orderId) {
        Decay d = decays.get(orderId);
        return d != null && d.remainingMicros <= 0;
    }
}
