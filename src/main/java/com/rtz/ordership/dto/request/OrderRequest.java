package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pedido manual: lo carga el operador cuando el cliente ya se lo confirmó, así que nace confirmado y con
 * fecha de entrega ("lo antes posible" = hoy, lo manda la app).
 */
public record OrderRequest(
        @NotNull(message = "El ID del cliente es obligatorio") UUID customerId,
        // Opcional: se puede asignar después, igual que en los pedidos de Shopify
        UUID customerAddressId,
        @NotNull(message = "Elegí la fecha de entrega") LocalDate deliveryDate,
        String notes,
        @DecimalMin(value = "0", message = "El costo de envío no puede ser negativo") BigDecimal deliveryFee,
        @DecimalMin(value = "0", message = "El descuento no puede ser negativo") BigDecimal discount,
        // Sin pagar si no viene
        PaymentStatus paymentStatus,
        @NotEmpty(message = "El pedido debe tener al menos un ítem") @Valid List<OrderItemRequest> items) {
}
