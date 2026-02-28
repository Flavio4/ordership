package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CustomerAddressRequest(
        @NotNull(message = "El ID de la zona es obligatorio") UUID zoneId,

        @NotBlank(message = "La etiqueta es obligatoria (ej: Casa, Trabajo)") String label,

        @NotBlank(message = "La calle o dirección principal es obligatoria") String street,

        String city,

        @NotBlank(message = "La descripción de la ubicación es obligatoria") String description,

        @NotNull(message = "La latitud es obligatoria") Double latitude,

        @NotNull(message = "La longitud es obligatoria") Double longitude,

        Boolean isDefault) {
}
