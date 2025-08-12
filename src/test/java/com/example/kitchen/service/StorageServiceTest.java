package com.example.kitchen.service;
import com.example.kitchen.enums.StorageType;
import com.example.kitchen.enums.Temperature;
import com.example.kitchen.events.RemoveResult;
import com.example.kitchen.model.Order;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StorageService (pure in-memory logic).
 * We avoid Thread.sleep by choosing placedAt times relative to Instant.now()
 * so that items are already fresh/expired at the moment we add them.
 */
class StorageServiceTest {

    // Real service (no mocks)
    private final StorageService svc = new StorageService();

    private static Order order(String id, String name, Temperature t, int freshSec, Instant placedAt) {
        return new Order(id, name, t, freshSec, placedAt);
    }

    // --- tryAdd + discard: COLD on SHELF is already expired (2x decay) and gets discarded
    @Test
    void shouldDiscardColdFromShelf_whenAlreadyExpiredAtPlacement() {
        Instant now = Instant.now();
        // freshness=3s, placed 2s ago -> on SHELF (non-ideal, 2x) ~4s consumed > 3s => expired
        Order o = order("exp1", "Yogurt", Temperature.COLD, 3, now.minusSeconds(2));

        assertTrue(svc.tryAddOrder(StorageType.SHELF, o).block());

        StepVerifier.create(svc.discardMinFromShelf())
                .expectNextMatches(ev -> ev.order().id().equals("exp1") && ev.from() == StorageType.SHELF)
                .verifyComplete();
    }

    // --- tryMoveOneFromShelf: move a COLD item from SHELF (non-ideal) to COOLER (ideal) if there is capacity
    @Test
    void shouldMoveOneFromShelfToCooler_whenCoolerHasCapacity() {
        Instant now = Instant.now();
        Order o = order("c1", "Cola", Temperature.COLD, 30, now);

        assertTrue(svc.tryAddOrder(StorageType.SHELF, o).block());

        StepVerifier.create(svc.tryMoveOneFromShelf())
                .expectNextMatches(ev ->
                        ev.order().id().equals("c1") &&
                                ev.from() == StorageType.SHELF &&
                                ev.to() == StorageType.COOLER)
                .verifyComplete();

        // Proof that it actually sits in COOLER now: removing by id from COOLER must succeed
        RemoveResult rr = svc.removeByIdWithExpiry(StorageType.COOLER, "c1").block();
        assertNotNull(rr);
        assertTrue(rr.removed());
    }

    // --- tryMoveOneFromShelf: do not move when the destination (COOLER) is full
    @Test
    void shouldNotMoveFromShelf_whenCoolerIsFull() {
        Instant now = Instant.now();
        // Fill COOLER to capacity = 6
        for (int i = 1; i <= 6; i++) {
            Order cold = order("cc" + i, "Can", Temperature.COLD, 60, now);
            assertTrue(svc.tryAddOrder(StorageType.COOLER, cold).block());
        }
        // Another COLD goes to SHELF; cannot be moved now
        Order extra = order("c-extra", "Can", Temperature.COLD, 60, now);
        assertTrue(svc.tryAddOrder(StorageType.SHELF, extra).block());

        StepVerifier.create(svc.tryMoveOneFromShelf()).verifyComplete(); // empty -> no move performed

        // The item is still on SHELF (it can be removed from SHELF; existence check)
        RemoveResult rr = svc.removeByIdWithExpiry(StorageType.SHELF, "c-extra").block();
        assertNotNull(rr);
        assertTrue(rr.removed());
    }

    // --- discardMinFromShelf: discard the soonest-to-expire item first
    @Test
    void shouldDiscardSoonestExpiryOnShelf() {
        Instant now = Instant.now();
        // 'fast' is already expired (fresh=2s, placed 2s ago on 2x => ~4s consumed)
        Order fast = order("fast", "Ice", Temperature.COLD, 2, now.minusSeconds(2));
        Order slow = order("slow", "Ice", Temperature.COLD, 20, now);

        assertTrue(svc.tryAddOrder(StorageType.SHELF, fast).block());
        assertTrue(svc.tryAddOrder(StorageType.SHELF, slow).block());

        StepVerifier.create(svc.discardMinFromShelf())
                .expectNextMatches(ev -> ev.order().id().equals("fast"))
                .verifyComplete();

        // 'slow' remains on SHELF (removal by id from SHELF must now succeed)
        RemoveResult rr = svc.removeByIdWithExpiry(StorageType.SHELF, "slow").block();
        assertNotNull(rr);
        assertTrue(rr.removed());
    }

    // --- removeByIdWithExpiry: on ideal storage it should NOT be expired within its budget
    @Test
    void shouldReportNotExpired_onIdealWhenWithinBudget() {
        Instant now = Instant.now();
        // HOT on HEATER (ideal), fresh=5s, placed 3s ago => ~2s remaining
        Order hot = order("hot1", "Soup", Temperature.HOT, 5, now.minusSeconds(3));

        assertTrue(svc.tryAddOrder(StorageType.HEATER, hot).block());

        RemoveResult rr = svc.removeByIdWithExpiry(StorageType.HEATER, "hot1").block();
        assertNotNull(rr);
        assertTrue(rr.removed());
        assertFalse(rr.expired());
    }

    // --- removeByIdWithExpiry: on non-ideal storage it SHOULD be expired when budget is consumed (2x rate)
    @Test
    void shouldReportExpired_onShelfWhenBudgetConsumed() {
        Instant now = Instant.now();
        // COLD on SHELF (non-ideal 2x), fresh=3s, placed 2s ago => ~4s consumed > 3s
        Order cold = order("exp2", "Yogurt", Temperature.COLD, 3, now.minusSeconds(2));

        assertTrue(svc.tryAddOrder(StorageType.SHELF, cold).block());

        RemoveResult rr = svc.removeByIdWithExpiry(StorageType.SHELF, "exp2").block();
        assertNotNull(rr);
        assertTrue(rr.removed());
        assertTrue(rr.expired());
    }

    // --- capacity: HEATER has 6 slots; the 7th add must return false
    @Test
    void shouldReturnFalseWhenHeaterCapacityReached() {
        Instant now = Instant.now();
        for (int i = 1; i <= 6; i++) {
            Order o = order("h" + i, "H", Temperature.HOT, 60, now);
            assertTrue(svc.tryAddOrder(StorageType.HEATER, o).block());
        }
        Order extra = order("hX", "H", Temperature.HOT, 60, now);
        assertFalse(svc.tryAddOrder(StorageType.HEATER, extra).block());
    }

    // --- removeByIdWithExpiry: if an id is missing, we should get removed=false, expired=false
    @Test
    void shouldReturnNotRemovedAndNotExpired_whenIdNotFound() {
        RemoveResult rr = svc.removeByIdWithExpiry(StorageType.SHELF, "missing").block();
        assertNotNull(rr);
        assertFalse(rr.removed());
        assertFalse(rr.expired());
    }
}