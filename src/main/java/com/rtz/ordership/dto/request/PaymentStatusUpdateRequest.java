package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.PaymentMethod;
import com.rtz.ordership.entity.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * {@code paymentMethod} es opcional: sin pagar lo borra; pagado o parcial sin forma de pago deja la que tenía.
 */
public record PaymentStatusUpdateRequest(
        @NotNull(message = "El estado de pago es obligatorio") PaymentStatus paymentStatus,
        PaymentMethod paymentMethod) {
}
