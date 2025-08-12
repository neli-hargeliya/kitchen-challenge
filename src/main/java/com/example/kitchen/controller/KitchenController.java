package com.example.kitchen.controller;


import com.example.kitchen.model.ActionEntity;
import com.example.kitchen.model.Order;
import com.example.kitchen.repository.ActionRepository;
import com.example.kitchen.service.KitchenService;
import com.example.kitchen.service.KitchenSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@Tag(name = "Kitchen API", description = "Operations for managing kitchen orders and simulations")
public record KitchenController(
        KitchenService kitchenService,
        ActionRepository actionRepository,
        KitchenSimulator kitchenSimulator
) {

    @PostMapping("/orders")
    @Operation(summary = "Place new order manually")
    public Mono<Void> placeOrder(@RequestBody Order order) {
        return kitchenService.placeOrder(order);
    }

    @PostMapping("/orders/{id}/pickup")
    @Operation(summary = "Pickup order by ID")
    public Mono<Void> pickupOrder(@PathVariable String id) {
        return kitchenService.pickupOrder(id);
    }

    @GetMapping("/ledger")
    @Operation(summary = "Get all actions from ledger")
    public Flux<ActionEntity> getLedger() {
        return actionRepository.findAll();
    }

    @PostMapping("/simulation/run")
    @Operation(summary = "Run kitchen simulation with Challenge API")
    public Mono<String> runSimulation(
            @RequestParam(defaultValue = "2") int ratePerSecond,
            @RequestParam(defaultValue = "4") int minPickupSec,
            @RequestParam(defaultValue = "8") int maxPickupSec
    ) {
        return Mono.fromRunnable(() -> kitchenSimulator.runSimulation(ratePerSecond, minPickupSec, maxPickupSec))
                .thenReturn("Simulation started with rate=" + ratePerSecond + " orders/sec");
    }
}

