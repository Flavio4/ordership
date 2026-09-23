package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.ShippingMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderRequest(
        @NotNull(message = "El ID del cliente es obligatorio") UUID customerId,
        @NotNull(message = "El ID de la dirección es obligatorio") UUID customerAddressId,
        LocalDate deliveryDate,
        String notes,
        ShippingMethod shippingMethod,
        String courierName,
        String trackingCode,
        @NotEmpty(message = "El pedido debe tener al menos un ítem") @Valid List<OrderItemRequest> items) {
}
