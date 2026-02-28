package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

public record CustomerRequest(
                @NotBlank(message = "El nombre completo es obligatorio") String fullName,

                @NotBlank(message = "El celular es obligatorio") String phone,

                @Email(message = "Formato de email inválido") String email,

                String notes) {
}
