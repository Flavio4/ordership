package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.Currency;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        String name,
        String description,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        String unit,
        Currency currency,
        Boolean active,
        Integer stock) {
}
