package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerName,
        String customerPhone,
        String addressLabel,
        String addressMapUrl,
        String zoneName,
        OrderStatus status,
        BigDecimal totalAmount,
        String notes,
        LocalDate deliveryDate,
        List<OrderItemResponse> items,
        String createdByName,
        Instant createdAt,
        Instant updatedAt) {
    public static OrderResponse fromEntity(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer().getFullName(),
                order.getCustomer().getPhone(),
                order.getCustomerAddress().getLabel(),
                order.getCustomerAddress().getMapUrl(),
                order.getCustomerAddress().getZone() != null
                        ? order.getCustomerAddress().getZone().getName()
                        : null,
                order.getStatus(),
                order.getTotalAmount(),
                order.getNotes(),
                order.getDeliveryDate(),
                order.getItems().stream()
                        .map(OrderItemResponse::fromEntity)
                        .toList(),
                order.getCreatedBy().getFullName(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
