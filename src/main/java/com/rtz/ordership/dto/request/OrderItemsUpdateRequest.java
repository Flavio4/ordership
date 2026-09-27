package com.rtz.ordership.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/** Reemplaza los productos de un pedido manual (y su envío y descuento); null en envío o descuento = 0. */
public record OrderItemsUpdateRequest(
        @NotEmpty(message = "El pedido debe tener al menos un ítem") @Valid List<OrderItemRequest> items,
        @DecimalMin(value = "0", message = "El costo de envío no puede ser negativo") BigDecimal deliveryFee,
        @DecimalMin(value = "0", message = "El descuento no puede ser negativo") BigDecimal discount) {
}
