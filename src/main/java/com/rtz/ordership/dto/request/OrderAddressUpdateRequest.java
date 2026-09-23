package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderAddressUpdateRequest(
        @NotNull(message = "El ID de la dirección es obligatorio") UUID customerAddressId) {
}
