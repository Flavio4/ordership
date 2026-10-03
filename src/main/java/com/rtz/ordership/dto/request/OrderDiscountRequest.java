package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderDiscountRequest(
        @Size(max = 100, message = "El motivo del descuento es demasiado largo") String label,
        @NotNull(message = "Ingresá el monto del descuento")
        @DecimalMin(value = "0", inclusive = false, message = "El descuento tiene que ser mayor a 0") BigDecimal amount) {
}
