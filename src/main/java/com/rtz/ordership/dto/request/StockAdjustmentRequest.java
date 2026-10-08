package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StockAdjustmentRequest(
        @NotNull(message = "La cantidad a ajustar es obligatoria") Integer delta,
        @Size(max = 255, message = "El motivo no puede superar los 255 caracteres") String reason) {
}
