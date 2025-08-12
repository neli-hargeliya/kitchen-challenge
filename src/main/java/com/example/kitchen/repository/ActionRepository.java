package com.example.kitchen.repository;

import com.example.kitchen.model.ActionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionRepository extends ReactiveCrudRepository<ActionEntity, Long> {
}
