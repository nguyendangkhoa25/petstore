package com.chtrembl.petstore.order.repository;

import com.azure.spring.data.cosmos.repository.CosmosRepository;
import com.chtrembl.petstore.order.entity.OrderEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends CosmosRepository<OrderEntity, String> {
    List<OrderEntity> findByEmail(String email);
}
