package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(
        @NotNull(message = "La cantidad a ajustar es obligatoria") Integer delta) {
}
