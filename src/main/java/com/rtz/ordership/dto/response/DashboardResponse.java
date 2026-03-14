package com.rtz.ordership.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        Map<String, Long> ordersByStatusToday,
        long totalOrdersToday,
        BigDecimal totalRevenueToday,
        BigDecimal totalRevenueWeek,
        long pendingOrders,
        long inTransitOrders,
        List<ZoneDeliveryCount> deliveriesByZone) {
    public record ZoneDeliveryCount(String zoneName, long count) {
    }
}
