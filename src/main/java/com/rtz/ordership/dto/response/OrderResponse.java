package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.PaymentStatus;
import com.rtz.ordership.entity.enums.ShippingMethod;

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
                PaymentStatus paymentStatus,
                OrderSource source,
                ShippingMethod shippingMethod,
                String courierName,
                String trackingCode,
                BigDecimal totalAmount,
                String notes,
                LocalDate deliveryDate,
                List<OrderItemResponse> items,
                String createdByName,
                String deliveryUserName,
                Instant confirmedAt,
                Instant createdAt,
                Instant updatedAt) {
        public static OrderResponse fromEntity(Order order) {
                String deliveryUser = null;
                if (order.getDeliveryAssignment() != null) {
                        deliveryUser = order.getDeliveryAssignment().getDeliveryUser().getFullName();
                }

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
                                order.getPaymentStatus(),
                                order.getSource(),
                                order.getShippingMethod(),
                                order.getCourierName(),
                                order.getTrackingCode(),
                                order.getTotalAmount(),
                                order.getNotes(),
                                order.getDeliveryDate(),
                                order.getItems().stream()
                                                .map(OrderItemResponse::fromEntity)
                                                .toList(),
                                order.getCreatedBy().getFullName(),
                                deliveryUser,
                                order.getConfirmedAt(),
                                order.getCreatedAt(),
                                order.getUpdatedAt());
        }
}
