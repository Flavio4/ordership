package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.StockMovement;
import com.rtz.ordership.entity.enums.StockMovementType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        StockMovementType type,
        int quantity,
        int stockAfter,
        String reason,
        UUID orderId,
        Long orderNumber,
        String shopifyOrderName,
        String userName,
        Instant createdAt) {

    public static StockMovementResponse fromEntity(StockMovement movement) {
        Order order = movement.getOrder();
        return new StockMovementResponse(
                movement.getId(),
                movement.getType(),
                movement.getQuantity(),
                movement.getStockAfter(),
                movement.getReason(),
                order != null ? order.getId() : null,
                order != null ? order.getOrderNumber() : null,
                order != null && order.getShopify() != null ? order.getShopify().getOrderName() : null,
                movement.getUser() != null ? movement.getUser().getFullName() : null,
                movement.getCreatedAt());
    }
}
