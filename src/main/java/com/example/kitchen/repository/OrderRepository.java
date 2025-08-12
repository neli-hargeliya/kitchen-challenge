package com.example.kitchen.repository;

import com.example.kitchen.model.OrderEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends ReactiveCrudRepository<OrderEntity, String> {
}
