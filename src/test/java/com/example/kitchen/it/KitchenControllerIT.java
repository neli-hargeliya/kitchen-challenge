package com.example.kitchen.it;

import com.example.kitchen.enums.Temperature;
import com.example.kitchen.model.ActionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class KitchenControllerIT extends AbstractR2dbcIT {

    @Autowired WebTestClient web;
    @Autowired DatabaseClient db;

    private void postOrder(String id, String name, Temperature temp, int freshness) {
        web.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "id", id,
                        "name", name,
                        "temp", temp.name(),
                        "freshness", freshness
                ))
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    private void pickup(String id) {
        web.post().uri("/api/orders/{id}/pickup", id)
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    void shouldReturnOkAndWritePlace_whenPostOrder() {
        postOrder("ord-1", "Cheese Pizza", Temperature.HOT, 120);

        var actions = web.get().uri("/api/ledger")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(ActionEntity.class)
                .returnResult()
                .getResponseBody();

        assertThat(actions).isNotNull();
        assertThat(actions).anySatisfy(a -> {
            assertThat(a.getOrderId()).isEqualTo("ord-1");
            assertThat(a.getAction().name()).isEqualTo("PLACE");
        });
    }

    @Test
    void shouldWritePickup_whenImmediatePickupOnIdealStorage() {
        postOrder("ord-2", "Soup", Temperature.HOT, 60); // goes to HEATER (ideal)
        pickup("ord-2");

        var actions = web.get().uri("/api/ledger")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(ActionEntity.class)
                .returnResult()
                .getResponseBody();

        assertThat(actions).isNotNull();
        // expect PLACE then PICKUP for ord-2
        assertThat(actions).filteredOn(a -> "ord-2".equals(a.getOrderId()))
                .extracting(a -> a.getAction().name())
                .contains("PLACE", "PICKUP");
    }

    @Test
    void shouldWriteDiscard_whenPickupAfterExpiryOnShelf() throws Exception {
        // Fill COOLER capacity=6 with COLD items, so next COLD goes to SHELF (non-ideal)
        for (int i = 1; i <= 6; i++) {
            postOrder("cold-"+i, "Cola", Temperature.COLD, 60);
        }
        // 7th COLD will be forced to SHELF; set tiny freshness so it expires quickly
        postOrder("cold-exp", "Ice Cream", Temperature.COLD, 1);

        // Wait a bit so that on SHELF (2x) it expires (1s budget -> ~600ms is enough, give 800ms)
        Thread.sleep(800);

        pickup("cold-exp");

        var actions = web.get().uri("/api/ledger")
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBodyList(ActionEntity.class)
                .returnResult()
                .getResponseBody();

        assertThat(actions).isNotNull();
        assertThat(actions).filteredOn(a -> "cold-exp".equals(a.getOrderId()))
                .extracting(a -> a.getAction().name())
                .contains("PLACE", "DISCARD");
    }
}
