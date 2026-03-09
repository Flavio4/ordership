package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.enums.DeliveryStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAssignmentResponse(
        UUID id,
        UUID orderId,
        String customerName,
        String addressMapUrl,
        String zoneName,
        String deliveryUserName,
        DeliveryStatus status,
        String notes,
        Instant assignedAt,
        Instant completedAt) {
    public static DeliveryAssignmentResponse fromEntity(DeliveryAssignment da) {
        return new DeliveryAssignmentResponse(
                da.getId(),
                da.getOrder().getId(),
                da.getOrder().getCustomer().getFullName(),
                da.getOrder().getCustomerAddress().getMapUrl(),
                da.getZone().getName(),
                da.getDeliveryUser().getFullName(),
                da.getStatus(),
                da.getNotes(),
                da.getAssignedAt(),
                da.getCompletedAt());
    }
}
