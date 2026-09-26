package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.Unit;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        String name,
        String description,
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio de compra debe ser mayor a 0") BigDecimal purchasePrice,
        @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0") BigDecimal salePrice,
        Unit unit,
        Currency currency,
        Boolean active,
        String shopifySku) {
}
