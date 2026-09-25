package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.CarrierType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CarrierRequest(
        @NotBlank(message = "El nombre del repartidor es obligatorio") String name,
        @NotNull(message = "El tipo de repartidor es obligatorio (OWN o COURIER)") CarrierType type,
        String phone,
        UUID userId,
        Boolean active) {
}
