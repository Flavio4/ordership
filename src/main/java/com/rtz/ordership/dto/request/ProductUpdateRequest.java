package com.rtz.ordership.dto.request;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        String name,
        String description,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        String unit,
        Boolean active) {
}
