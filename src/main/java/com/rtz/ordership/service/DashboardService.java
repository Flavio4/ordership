package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.DashboardResponse;
import com.rtz.ordership.dto.response.DashboardResponse.DailySales;
import com.rtz.ordership.dto.response.DashboardResponse.Period;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.PaymentStatus;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
public class DashboardService {

    private static final int DAYS = 7;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ShopifyWebhookFailureRepository failureRepository;
    // El servidor corre en UTC: los días se cuentan con la hora del negocio
    private final ZoneId zone;

    public DashboardService(OrderRepository orderRepository,
            ProductRepository productRepository,
            ShopifyWebhookFailureRepository failureRepository,
            @Value("${app.timezone:America/Asuncion}") String timezone) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.failureRepository = failureRepository;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        return getDashboard(Clock.system(zone));
    }

    DashboardResponse getDashboard(Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.minusDays(DAYS - 1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate previousMonthStart = monthStart.minusMonths(1);
        // Mismo tramo del mes anterior: del 1 al mismo día (o al último, si el mes anterior es más corto)
        LocalDate previousMonthEnd = previousMonthStart.plusDays(
                Math.min(today.getDayOfMonth(), previousMonthStart.lengthOfMonth()) - 1);
        LocalDate since = firstDay.isBefore(previousMonthStart) ? firstDay : previousMonthStart;

        List<Sale> sales = orderRepository.salesSince(since.atStartOfDay(zone).toInstant()).stream()
                .map(this::toSale)
                .toList();

        List<DailySales> lastSevenDays = firstDay.datesUntil(today.plusDays(1))
                .map(day -> period(sales, day, day))
                .map(period -> new DailySales(period.from(), period.orders(), period.revenue(), period.profit()))
                .toList();
        Period todayTotals = period(sales, today, today);
        Period week = period(sales, today.with(DayOfWeek.MONDAY), today);

        DashboardResponse dashboard = new DashboardResponse(
                today,
                todayTotals.orders(),
                todayTotals.revenue(),
                week.revenue(),
                orderRepository.countByStatusIn(List.of(OrderStatus.PENDING)),
                orderRepository.countByStatusIn(List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT)),
                orderRepository.countScheduledOn(today, null),
                orderRepository.countScheduledBefore(today, null),
                productRepository.countByActiveTrueAndNeedsReviewTrue(),
                failureRepository.countByResolvedAtIsNull(),
                orderRepository.countDeliveredWithoutCostSince(
                        OrderService.missingDeliveryCostSince(clock.instant())),
                todayTotals,
                week,
                period(sales, monthStart, today),
                period(sales, previousMonthStart, previousMonthEnd),
                lastSevenDays);
        log.info("Dashboard del {} - pedidos hoy: {}, ventas hoy: {}, ganancia hoy: {}, pendientes: {}",
                today, dashboard.ordersToday(), dashboard.revenueToday(), todayTotals.profit(),
                dashboard.pendingOrders());
        return dashboard;
    }

    /**
     * Resumen de un período elegido [from, to] (días en hora del negocio). Sin from: desde el primer pedido.
     * Sin to: hasta hoy. Lo suma la base, así un período largo no trae todos los pedidos.
     */
    @Transactional(readOnly = true)
    public Period getSummary(LocalDate from, LocalDate to) {
        return getSummary(from, to, Clock.system(zone));
    }

    Period getSummary(LocalDate from, LocalDate to, Clock clock) {
        LocalDate until = to != null ? to : LocalDate.now(clock);
        if (from != null && from.isAfter(until)) {
            throw new IllegalArgumentException("La fecha \"desde\" no puede ser posterior a \"hasta\"");
        }
        Instant start = from != null ? from.atStartOfDay(zone).toInstant() : Instant.EPOCH;
        Instant before = until.plusDays(1).atStartOfDay(zone).toInstant();

        // [pedidos, vendido, delivery, pagados, cobrado, delivery de los pagados, primer pedido]
        Object[] orders = orderRepository.orderTotals(start, before).getFirst();
        // [costo de productos, costo de productos de los pagados, pedidos sin costo, pagados sin costo]
        Object[] items = orderRepository.itemTotals(start, before).getFirst();

        BigDecimal revenue = decimal(orders[1]);
        BigDecimal collectedRevenue = decimal(orders[4]);
        LocalDate firstDay = from != null ? from
                : orders[6] != null ? ((Instant) orders[6]).atZone(zone).toLocalDate() : until;
        Period summary = new Period(firstDay, until,
                ((Number) orders[0]).longValue(),
                revenue,
                revenue.subtract(decimal(items[0])).subtract(decimal(orders[2])),
                ((Number) items[2]).longValue(),
                ((Number) orders[3]).longValue(),
                collectedRevenue,
                collectedRevenue.subtract(decimal(items[1])).subtract(decimal(orders[5])),
                ((Number) items[3]).longValue());
        log.info("Resumen del {} al {} - pedidos: {}, ventas: {}, ganancia: {}",
                summary.from(), summary.to(), summary.orders(), summary.revenue(), summary.profit());
        return summary;
    }

    private record Sale(LocalDate day, BigDecimal revenue, BigDecimal profit, boolean withoutCost, boolean paid) {
    }

    // [createdAt, amountToCollect, costo de productos, costo del delivery, ítems sin costo, estado del pago]
    private Sale toSale(Object[] row) {
        BigDecimal revenue = decimal(row[1]);
        BigDecimal profit = revenue.subtract(decimal(row[2])).subtract(decimal(row[3]));
        return new Sale(((Instant) row[0]).atZone(zone).toLocalDate(), revenue, profit,
                ((Number) row[4]).longValue() > 0, row[5] == PaymentStatus.PAID);
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static Period period(List<Sale> sales, LocalDate from, LocalDate to) {
        long orders = 0;
        long withoutCost = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal profit = BigDecimal.ZERO;
        long collectedOrders = 0;
        long collectedWithoutCost = 0;
        BigDecimal collectedRevenue = BigDecimal.ZERO;
        BigDecimal collectedProfit = BigDecimal.ZERO;
        for (Sale sale : sales) {
            if (sale.day().isBefore(from) || sale.day().isAfter(to)) continue;
            orders++;
            revenue = revenue.add(sale.revenue());
            profit = profit.add(sale.profit());
            if (sale.withoutCost()) withoutCost++;
            if (!sale.paid()) continue;
            collectedOrders++;
            collectedRevenue = collectedRevenue.add(sale.revenue());
            collectedProfit = collectedProfit.add(sale.profit());
            if (sale.withoutCost()) collectedWithoutCost++;
        }
        return new Period(from, to, orders, revenue, profit, withoutCost,
                collectedOrders, collectedRevenue, collectedProfit, collectedWithoutCost);
    }
}
