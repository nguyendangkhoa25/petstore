package com.chtrembl.petstore.pet.entity;

import com.chtrembl.petstore.pet.model.Pet;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pet")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    @Column(nullable = false)
    private String name;

    @Column(name = "photoURL", nullable = false)
    private String photoURL;

    @Column(nullable = false, length = 64)
    @Enumerated(EnumType.STRING)
    private Pet.Status status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "pet_tag",
            joinColumns = @JoinColumn(name = "pet_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<TagEntity> tags = new ArrayList<>();
}
