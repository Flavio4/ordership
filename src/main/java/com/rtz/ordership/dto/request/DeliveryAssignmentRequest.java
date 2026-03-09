package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryAssignmentRequest(
        @NotNull(message = "El ID del pedido es obligatorio") UUID orderId,
        @NotNull(message = "El ID del repartidor es obligatorio") UUID deliveryUserId,
        String notes) {
}
