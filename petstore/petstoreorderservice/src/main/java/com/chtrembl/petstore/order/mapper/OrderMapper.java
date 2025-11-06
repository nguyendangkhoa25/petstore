package com.chtrembl.petstore.order.mapper;

import com.chtrembl.petstore.order.entity.OrderEntity;
import com.chtrembl.petstore.order.entity.ProductEntity;
import com.chtrembl.petstore.order.model.Order;
import com.chtrembl.petstore.order.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "status", expression = "java(Order.Status.fromValue(entity.getStatus()))")
    Order toModel(OrderEntity entity);

    @Mapping(target = "status", expression = "java(order.getStatus() != null ? order.getStatus().toString() : null)")
    OrderEntity toEntity(Order order);

    List<Order> toModels(List<OrderEntity> entities);

    List<Product> toModelProducts(List<ProductEntity> entities);
}
