package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.Unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        Unit unit,
        Currency currency,
        Boolean active,
        Integer stock,
        Instant createdAt) {
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPurchasePrice(),
                product.getSalePrice(),
                product.getUnit(),
                product.getCurrency(),
                product.getActive(),
                product.getStock(),
                product.getCreatedAt());
    }
}
