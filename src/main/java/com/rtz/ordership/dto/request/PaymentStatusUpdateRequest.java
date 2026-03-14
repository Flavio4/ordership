package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record PaymentStatusUpdateRequest(
        @NotNull(message = "El estado de pago es obligatorio") PaymentStatus paymentStatus) {
}
