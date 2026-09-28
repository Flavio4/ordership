package com.rtz.ordership.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen para la pantalla de inicio: pedidos de Shopify y manuales; "hoy" es el día en Paraguay (app.timezone).
 * Ventas = amountToCollect de los pedidos no cancelados, por día de creación.
 * Ganancia = ventas - costo de los productos - costo del delivery (ver OrderProfitResponse).
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
        // Entregados en los últimos 30 días sin el costo del delivery cargado
        long deliveredWithoutCost,
        Period todayTotals,
        Period week,
        // Del 1 a hoy, y el mismo tramo del mes anterior para comparar
        Period month,
        Period previousMonth,
        // Los últimos 7 días, del más viejo a hoy (incluye los días sin ventas)
        List<DailySales> lastSevenDays) {

    public record DailySales(LocalDate date, long orders, BigDecimal revenue, BigDecimal profit) {
    }

    /**
     * @param ordersWithoutCost pedidos con algún producto sin precio de compra: la ganancia real es menor
     */
    public record Period(LocalDate from, LocalDate to, long orders, BigDecimal revenue, BigDecimal profit,
            long ordersWithoutCost) {
    }
}
