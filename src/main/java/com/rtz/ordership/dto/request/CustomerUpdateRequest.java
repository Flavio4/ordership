package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.Email;

public record CustomerUpdateRequest(
                String fullName,
                String phone,
                @Email(message = "Formato de email inválido") String email,
                String notes,
                Boolean active) {
}
