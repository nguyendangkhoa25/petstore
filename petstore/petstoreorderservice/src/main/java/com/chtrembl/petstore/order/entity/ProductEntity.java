package com.chtrembl.petstore.order.entity;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity {
    private Long id;
    private Integer quantity;
    private String name;
    private String photoURL;
}
