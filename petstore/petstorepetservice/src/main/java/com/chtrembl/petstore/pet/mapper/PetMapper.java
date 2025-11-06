package com.chtrembl.petstore.pet.mapper;

import com.chtrembl.petstore.pet.entity.*;
import com.chtrembl.petstore.pet.model.*;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface PetMapper {

    //@Mapping(target = "status", expression = "java(Pet.Status.fromValue(entity.getStatus()))")
    Pet toModel(PetEntity entity);

    @InheritInverseConfiguration
    //@Mapping(target = "status", expression = "java(model.getStatus() != null ? model.getStatus().getValue() : null)")
    PetEntity toEntity(Pet model);

    Category toModel(CategoryEntity entity);
    CategoryEntity toEntity(Category model);

    Tag toModel(TagEntity entity);
    TagEntity toEntity(Tag model);

    List<Pet> toModelList(List<PetEntity> entities);
}