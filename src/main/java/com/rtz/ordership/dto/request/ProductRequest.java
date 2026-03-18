package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.Unit;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
                @NotBlank(message = "El nombre del producto es obligatorio") String name,

                String description,

                @NotNull(message = "El precio de compra es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de compra debe ser mayor a 0") BigDecimal purchasePrice,

                @NotNull(message = "El precio de venta es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0") BigDecimal salePrice,

                @NotNull(message = "La unidad es obligatoria") Unit unit,

                @NotNull(message = "La moneda es obligatoria") Currency currency,

                @NotNull(message = "El stock es obligatorio") @Min(value = 0, message = "El stock no puede ser negativo") Integer stock) {
}
