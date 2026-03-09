package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
        @NotNull(message = "El estado es obligatorio") OrderStatus status) {
}
