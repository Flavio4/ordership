package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ZoneRequest(
        @NotBlank(message = "El nombre de la zona es obligatorio") String name,

        String description) {
}
