package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CustomerAddressRequest(
                @NotNull(message = "El ID de la zona es obligatorio") UUID zoneId,

                String label,

                String street,

                String city,

                String description,

                Double latitude,

                Double longitude,

                @NotBlank(message = "El link del mapa es obligatorio") String mapUrl,

                Boolean isDefault) {
}
