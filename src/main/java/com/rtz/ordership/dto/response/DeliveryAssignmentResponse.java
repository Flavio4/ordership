package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.enums.CarrierType;
import com.rtz.ordership.entity.enums.DeliveryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeliveryAssignmentResponse(
        UUID id,
        UUID orderId,
        Long orderNumber,
        String customerName,
        String customerPhone,
        String addressMapUrl,
        String shippingAddressRaw,
        String zoneName,
        BigDecimal amountToCollect,
        UUID carrierId,
        String carrierName,
        CarrierType carrierType,
        String trackingCode,
        DeliveryStatus status,
        String notes,
        String failureReason,
        Instant assignedAt,
        Instant completedAt) {

    public static DeliveryAssignmentResponse fromEntity(DeliveryAssignment da) {
        Order order = da.getOrder();
        return new DeliveryAssignmentResponse(
                da.getId(),
                order.getId(),
                order.getOrderNumber(),
                order.getCustomer().getFullName(),
                order.getCustomer().getPhone(),
                order.getCustomerAddress() != null ? order.getCustomerAddress().getMapUrl() : null,
                order.getShippingAddressRaw(),
                da.getZone() != null ? da.getZone().getName() : null,
                order.getAmountToCollect(),
                da.getCarrier().getId(),
                da.getCarrier().getName(),
                da.getCarrier().getType(),
                order.getTrackingCode(),
                da.getStatus(),
                da.getNotes(),
                da.getFailureReason(),
                da.getAssignedAt(),
                da.getCompletedAt());
    }
}
