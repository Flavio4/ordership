package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderDeliveryRequest(
        @NotNull(message = "Elegí quién lleva el pedido") UUID carrierId,
        String trackingCode,
        String notes) {
}
