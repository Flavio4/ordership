package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.enums.CarrierType;
import com.rtz.ordership.entity.enums.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

/** Un intento de entrega tal como se muestra dentro del pedido. */
public record OrderDeliveryResponse(
        UUID id,
        UUID carrierId,
        String carrierName,
        CarrierType carrierType,
        String carrierPhone,
        DeliveryStatus status,
        String notes,
        String failureReason,
        Instant assignedAt,
        Instant completedAt) {

    public static OrderDeliveryResponse fromEntity(DeliveryAssignment delivery) {
        return new OrderDeliveryResponse(
                delivery.getId(),
                delivery.getCarrier().getId(),
                delivery.getCarrier().getName(),
                delivery.getCarrier().getType(),
                delivery.getCarrier().getPhone(),
                delivery.getStatus(),
                delivery.getNotes(),
                delivery.getFailureReason(),
                delivery.getAssignedAt(),
                delivery.getCompletedAt());
    }
}
