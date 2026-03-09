package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record DeliveryStatusUpdateRequest(
        @NotNull(message = "El estado es obligatorio") DeliveryStatus status,
        String notes) {
}
