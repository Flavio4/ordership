package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.Role;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "El email es obligatorio") @Email(message = "El email debe ser válido") String email,

        @NotBlank(message = "La contraseña es obligatoria") @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres") String password,

        @NotBlank(message = "El nombre completo es obligatorio") String fullName,

        String phone,

        @NotNull(message = "El rol es obligatorio") Role role) {
}
