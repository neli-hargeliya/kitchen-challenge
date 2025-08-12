package com.example.kitchen.service;

import com.example.kitchen.enums.ActionType;
import com.example.kitchen.enums.StorageType;
import com.example.kitchen.enums.Temperature;
import com.example.kitchen.events.RemoveResult;
import com.example.kitchen.mapper.ActionEntityMapper;
import com.example.kitchen.mapper.OrderEntityMapper;
import com.example.kitchen.model.ActionEntity;
import com.example.kitchen.model.Order;
import com.example.kitchen.model.OrderEntity;
import com.example.kitchen.repository.ActionRepository;
import com.example.kitchen.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenServiceTest {
    @Mock
    StorageService storageService;
    @Mock
    OrderRepository orderRepository;
    @Mock
    ActionRepository actionRepository;
    @Mock
    OrderEntityMapper orderEntityMapper;
    @Mock
    ActionEntityMapper actionEntityMapper;

    // Deep stubs to mock: template.insert(OrderEntity.class).using(entity)
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    R2dbcEntityTemplate template;
    @Mock
    TransactionalOperator tx;

    KitchenService service;

    @Captor
    org.mockito.ArgumentCaptor<ActionEntity> actionCaptor;

    @BeforeEach
    void setUp() {
        lenient().when(tx.transactional(any(Mono.class)))
                .thenAnswer(returnsFirstArg());
        lenient().when(tx.transactional(any(Flux.class)))
                .thenAnswer(returnsFirstArg());

        service = new KitchenService(
                storageService, orderRepository, actionRepository,
                orderEntityMapper, actionEntityMapper, template, tx
        );
    }

    @Test
    void shouldReturnCompleted_whenPlacedOnIdealStorageAndWritePlace() {
        Order in = new Order("o1", "Hot Dish", Temperature.HOT, 120, null);
        StorageType ideal = StorageType.HEATER;

        when(storageService.idealFor(Temperature.HOT)).thenReturn(ideal);
        when(storageService.tryAddOrder(eq(ideal), any(Order.class))).thenReturn(Mono.just(true));

        OrderEntity mapped = new OrderEntity();
        when(orderEntityMapper.toEntity(any(Order.class), eq(ideal))).thenReturn(mapped);
        when(template.insert(eq(OrderEntity.class)).using(eq(mapped))).thenReturn(Mono.just(mapped));

        ActionEntity placeAction = new ActionEntity();
        when(actionEntityMapper.toEntity("o1", ActionType.PLACE, ideal)).thenReturn(placeAction);
        when(actionRepository.save(placeAction)).thenReturn(Mono.just(placeAction));

        StepVerifier.create(service.placeOrder(in)).verifyComplete();

        verify(storageService).tryAddOrder(eq(ideal), any(Order.class));
        verify(storageService, never()).tryAddOrder(eq(StorageType.SHELF), any(Order.class));
        verify(actionRepository).save(placeAction);
    }

    @Test
    void shouldReturnCompleted_whenIdealIsFull_thenPlaceOnShelfAndWritePlace() {
        Order in = new Order("o2", "Cold Dish", Temperature.COLD, 60, null);

        when(storageService.idealFor(Temperature.COLD)).thenReturn(StorageType.COOLER);
        when(storageService.tryAddOrder(eq(StorageType.COOLER), any(Order.class))).thenReturn(Mono.just(false));
        when(storageService.tryAddOrder(eq(StorageType.SHELF), any(Order.class))).thenReturn(Mono.just(true));

        OrderEntity mapped = new OrderEntity();
        when(orderEntityMapper.toEntity(any(Order.class), eq(StorageType.SHELF))).thenReturn(mapped);
        when(template.insert(eq(OrderEntity.class)).using(eq(mapped))).thenReturn(Mono.just(mapped));

        ActionEntity placeAction = new ActionEntity();
        when(actionEntityMapper.toEntity("o2", ActionType.PLACE, StorageType.SHELF)).thenReturn(placeAction);
        when(actionRepository.save(placeAction)).thenReturn(Mono.just(placeAction));

        StepVerifier.create(service.placeOrder(in)).verifyComplete();

        verify(storageService).tryAddOrder(eq(StorageType.COOLER), any(Order.class));
        verify(storageService).tryAddOrder(eq(StorageType.SHELF), any(Order.class));
        verify(actionRepository).save(placeAction);
    }

    @Test
    void shouldReturnCompletedAndWritePickup_whenRemovedAndNotExpired() {
        String id = "p1";
        OrderEntity e = new OrderEntity();
        e.setId(id);
        e.setStorage(StorageType.COOLER);

        when(orderRepository.findById(id)).thenReturn(Mono.just(e));
        when(storageService.removeByIdWithExpiry(StorageType.COOLER, id))
                .thenReturn(Mono.just(new RemoveResult(true, false)));

        ActionEntity pickupAction = new ActionEntity();
        when(actionEntityMapper.toEntity(id, ActionType.PICKUP, StorageType.COOLER)).thenReturn(pickupAction);
        when(actionRepository.save(pickupAction)).thenReturn(Mono.just(pickupAction));
        when(orderRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.pickupOrder(id)).verifyComplete();

        verify(actionRepository).save(pickupAction);
        verify(orderRepository).deleteById(id);
    }

    @Test
    void shouldReturnCompletedAndWriteDiscard_whenRemovedAndExpired() {
        String id = "p2";
        OrderEntity e = new OrderEntity();
        e.setId(id);
        e.setStorage(StorageType.SHELF);

        when(orderRepository.findById(id)).thenReturn(Mono.just(e));
        when(storageService.removeByIdWithExpiry(StorageType.SHELF, id))
                .thenReturn(Mono.just(new RemoveResult(true, true)));

        ActionEntity discardAction = new ActionEntity();
        when(actionEntityMapper.toEntity(id, ActionType.DISCARD, StorageType.SHELF)).thenReturn(discardAction);
        when(actionRepository.save(discardAction)).thenReturn(Mono.just(discardAction));
        when(orderRepository.deleteById(id)).thenReturn(Mono.empty());

        StepVerifier.create(service.pickupOrder(id)).verifyComplete();

        verify(actionRepository).save(discardAction);
        verify(orderRepository).deleteById(id);
    }

    @Test
    void shouldReturnCompletedAndWriteNothing_whenNotRemovedFromStorageOnPickup() {
        String id = "p3";
        OrderEntity e = new OrderEntity();
        e.setId(id);
        e.setStorage(StorageType.HEATER);

        when(orderRepository.findById(id)).thenReturn(Mono.just(e));
        when(storageService.removeByIdWithExpiry(StorageType.HEATER, id))
                .thenReturn(Mono.just(new RemoveResult(false, false)));

        StepVerifier.create(service.pickupOrder(id)).verifyComplete();

        verify(actionRepository, never()).save(any());
        verify(orderRepository, never()).deleteById(anyString());
    }

    @Test
    void shouldReturnCompletedAndDoNothing_whenOrderMissingInDbOnPickup() {
        when(orderRepository.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.pickupOrder("missing")).verifyComplete();

        verify(actionRepository, never()).save(any());
        verify(storageService, never()).removeByIdWithExpiry(any(), anyString());
    }
}