package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        String unit,
        Boolean active,
        Instant createdAt) {
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPurchasePrice(),
                product.getSalePrice(),
                product.getUnit(),
                product.getActive(),
                product.getCreatedAt());
    }
}
