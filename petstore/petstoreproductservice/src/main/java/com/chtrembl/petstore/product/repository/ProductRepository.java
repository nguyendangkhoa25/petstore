package com.chtrembl.petstore.product.repository;

import com.chtrembl.petstore.product.entity.ProductEntity;
import com.chtrembl.petstore.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByStatusIn(List<Product.Status> statuses);
}