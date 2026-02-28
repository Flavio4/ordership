package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.Currency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
                @NotBlank(message = "El nombre del producto es obligatorio") String name,

                String description,

                @NotNull(message = "El precio de compra es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de compra debe ser mayor a 0") BigDecimal purchasePrice,

                @NotNull(message = "El precio de venta es obligatorio") @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0") BigDecimal salePrice,

                @NotBlank(message = "La unidad es obligatoria") String unit,

                @NotNull(message = "La moneda es obligatoria") Currency currency) {
}
