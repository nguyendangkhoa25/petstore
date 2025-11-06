package com.chtrembl.petstore.order.service;

import com.chtrembl.petstore.order.entity.OrderEntity;
import com.chtrembl.petstore.order.exception.OrderNotFoundException;
import com.chtrembl.petstore.order.mapper.OrderMapper;
import com.chtrembl.petstore.order.model.Order;
import com.chtrembl.petstore.order.model.Product;
import com.chtrembl.petstore.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductService productService;

    public Order getOrderById(String orderId) {
        log.info("Retrieving order {} from Cosmos DB", orderId);

        OrderEntity entity = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order " + orderId + " not found"));

        return orderMapper.toModel(entity);
    }

    public Order updateOrder(Order order) {
        log.info("Updating order {} in Cosmos DB", order.getId());

        // Product validation logic remains the same
        if (order.getProducts() != null && !order.getProducts().isEmpty()) {
            List<Product> availableProducts = productService.getAvailableProducts();
            validateProductsExist(order.getProducts(), availableProducts);
        }

        // Upsert (create or update)
        orderRepository.save(orderMapper.toEntity(order));
        return order;
    }

    /**
     * Validates that all products in the order exist in the available products list
     *
     * @param orderProducts     List of products from the order
     * @param availableProducts List of available products from Product Service
     * @throws IllegalArgumentException if any product is not found
     */
    private void validateProductsExist(List<Product> orderProducts, List<Product> availableProducts) {
        if (orderProducts == null || orderProducts.isEmpty()) {
            return;
        }

        List<Long> requestedProductIds = orderProducts.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        List<Long> availableProductIds = availableProducts.stream()
                .map(Product::getId)
                .filter(id -> id != null)
                .collect(Collectors.toList());

        List<Long> missingProductIds = requestedProductIds.stream()
                .filter(id -> !availableProductIds.contains(id))
                .collect(Collectors.toList());

        if (!missingProductIds.isEmpty()) {
            String errorMessage = String.format("Products with IDs %s are not available or do not exist",
                    missingProductIds);
            log.warn("Product validation failed for order: {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        log.debug("Product validation passed for {} products", requestedProductIds.size());
    }

    public void enrichOrderWithProductDetails(Order order, List<Product> availableProducts) {
        if (order.getProducts() == null || availableProducts == null) {
            log.warn("Cannot enrich order: order.products={}, availableProducts={}",
                    order.getProducts(), availableProducts != null ? availableProducts.size() : "null");
            return;
        }

        log.info("Enriching order {} with {} available products",
                order.getId(), availableProducts.size());

        for (Product orderProduct : order.getProducts()) {
            String originalName = orderProduct.getName();
            String originalURL = orderProduct.getPhotoURL();

            Optional<Product> foundProduct = availableProducts.stream()
                    .filter(p -> p.getId().equals(orderProduct.getId()))
                    .findFirst();

            if (foundProduct.isPresent()) {
                Product availableProduct = foundProduct.get();
                orderProduct.setName(availableProduct.getName());
                orderProduct.setPhotoURL(availableProduct.getPhotoURL());

                log.info("Enriched product {}: '{}' -> '{}', URL: '{}' -> '{}'",
                        orderProduct.getId(), originalName, availableProduct.getName(),
                        originalURL, availableProduct.getPhotoURL());
            } else {
                log.warn("Product with id {} not found in available products during enrichment",
                        orderProduct.getId());
            }
        }
    }
}