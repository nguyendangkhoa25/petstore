package com.chtrembl.petstore.product.service;

import com.chtrembl.petstore.product.entity.ProductEntity;
import com.chtrembl.petstore.product.mapper.ProductMapper;
import com.chtrembl.petstore.product.model.Product;
import com.chtrembl.petstore.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

    //private final DataPreload dataPreload;
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public List<Product> findProductsByStatus(List<String> status) {
        log.info("Finding products with status: {}", status);

        List<Product.Status> statuses = status.stream()
                .map(String::toUpperCase)
                .map(s -> {
                    try {
                        return Product.Status.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid status ignored: {}", s);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (statuses.isEmpty()) {
            log.warn("No valid statuses provided, returning empty list");
            return List.of();
        }

        return productMapper.toModelList(productRepository.findByStatusIn(statuses));
    }

    public Optional<Product> findProductById(Long productId) {
        log.info("Finding product with id: {}", productId);
        return productRepository.findById(productId)
                .map(productMapper::toModel);
    }

    public List<Product> getAllProducts() {
        log.info("Getting all products");
        return productMapper.toModelList(productRepository.findAll());
    }

    public int getProductCount() {
        return (int) productRepository.count();
    }
}