package com.chtrembl.petstore.order.entity;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Container(containerName = "orders")
public class OrderEntity {
    @Id
    private String id;

    @PartitionKey
    private String email;

    private List<ProductEntity> products;

    private String status;

    private Boolean complete;
}
