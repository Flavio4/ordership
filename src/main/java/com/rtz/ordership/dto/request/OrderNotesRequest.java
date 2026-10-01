package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.Size;

public record OrderNotesRequest(@Size(max = 2000, message = "La nota puede tener hasta 2000 caracteres") String notes) {
}
