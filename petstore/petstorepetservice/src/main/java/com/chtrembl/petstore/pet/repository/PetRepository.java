package com.chtrembl.petstore.pet.repository;

import com.chtrembl.petstore.pet.entity.PetEntity;
import com.chtrembl.petstore.pet.model.Pet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PetRepository extends JpaRepository<PetEntity, Long> {
    List<PetEntity> findByStatusIn(List<Pet.Status> statuses);
}