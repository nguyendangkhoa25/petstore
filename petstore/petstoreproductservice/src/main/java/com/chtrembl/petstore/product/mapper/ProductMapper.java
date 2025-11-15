package com.chtrembl.petstore.product.mapper;

import com.chtrembl.petstore.product.entity.*;
import com.chtrembl.petstore.product.model.*;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    //@Mapping(target = "status", expression = "java(Product.Status.fromValue(entity.getStatus()))")
    Product toModel(ProductEntity entity);

    //@InheritInverseConfiguration
    //@Mapping(target = "status", expression = "java(model.getStatus().getValue())")
    ProductEntity toEntity(Product model);

    Category toModel(CategoryEntity entity);
    CategoryEntity toEntity(Category model);

    Tag toModel(TagEntity entity);
    TagEntity toEntity(Tag model);

    List<Product> toModelList(List<ProductEntity> entities);
}