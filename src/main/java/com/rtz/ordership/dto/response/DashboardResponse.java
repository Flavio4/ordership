package com.rtz.ordership.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen para la pantalla de inicio: pedidos de Shopify y manuales; "hoy" es el día en Paraguay (app.timezone).
 * Ventas = amountToCollect de los pedidos no cancelados, por día de creación.
 */
public record DashboardResponse(
        LocalDate today,
        long ordersToday,
        BigDecimal revenueToday,
        // De lunes a hoy
        BigDecimal revenueWeek,
        long pendingOrders,
        // Asignados o en camino
        long inDeliveryOrders,
        long deliveriesToday,
        long deliveriesOverdue,
        long productsToReview,
        long webhookFailures,
        // Los últimos 7 días, del más viejo a hoy (incluye los días sin ventas)
        List<DailySales> lastSevenDays) {

    public record DailySales(LocalDate date, long orders, BigDecimal revenue) {
    }
}
