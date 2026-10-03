package com.rtz.ordership.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reemplaza los productos de un pedido (y su envío y descuentos); null en envío = 0. {@code discounts}, si viene
 * (aunque sea vacía), reemplaza a {@code discount}, que es un solo descuento sin motivo de las apps anteriores.
 */
public record OrderItemsUpdateRequest(
        @NotEmpty(message = "El pedido debe tener al menos un ítem") @Valid List<OrderItemRequest> items,
        @DecimalMin(value = "0", message = "El costo de envío no puede ser negativo") BigDecimal deliveryFee,
        @DecimalMin(value = "0", message = "El descuento no puede ser negativo") BigDecimal discount,
        @Valid List<OrderDiscountRequest> discounts) {
}
