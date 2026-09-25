package com.rtz.ordership.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ResolveWebhookFailureRequest(
        @NotBlank(message = "La nota es obligatoria") String note) {
}
