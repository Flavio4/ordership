package com.rtz.ordership.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Resumen de compras de un cliente: pedidos no cancelados y lo que se le cobró en total
public record CustomerOrderStats(UUID customerId, long orderCount, BigDecimal totalSpent, Instant lastOrderAt) {

    public static CustomerOrderStats empty(UUID customerId) {
        return new CustomerOrderStats(customerId, 0, BigDecimal.ZERO, null);
    }
}
