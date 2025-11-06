package com.chtrembl.petstore.pet.service;

import com.chtrembl.petstore.pet.entity.PetEntity;
import com.chtrembl.petstore.pet.mapper.PetMapper;
import com.chtrembl.petstore.pet.model.Pet;
import com.chtrembl.petstore.pet.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PetService {
    private final PetRepository petRepository;
    private final PetMapper petMapper;

    public List<Pet> findPetsByStatus(List<String> status) {
        log.info("Finding pets with status: {}", status);
        List<Pet.Status> statuses = status.stream()
                .map(String::toUpperCase)
                .map(s -> {
                    try {
                        return Pet.Status.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid status value ignored: {}", s);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (statuses.isEmpty()) {
            log.warn("No valid statuses provided, returning empty list");
            return List.of();
        }
        return petMapper.toModelList(petRepository.findByStatusIn(statuses));
    }

    public Optional<Pet> findPetById(Long petId) {
        log.info("Finding pet with id: {}", petId);
        return petRepository.findById(petId)
                .map(petMapper::toModel);
    }

    public List<Pet> getAllPets() {
        log.info("Getting all pets");
        return petMapper.toModelList(petRepository.findAll());
    }

    public int getPetCount() {
        return (int) petRepository.count();
    }
}