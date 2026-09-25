package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.Unit;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        String name,
        String description,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        Unit unit,
        Currency currency,
        Boolean active,
        String shopifySku) {
}
