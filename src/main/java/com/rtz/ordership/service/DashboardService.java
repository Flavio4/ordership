package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.DashboardResponse;
import com.rtz.ordership.dto.response.DashboardResponse.DailySales;
import com.rtz.ordership.dto.response.DashboardResponse.Period;
import com.rtz.ordership.entity.enums.OrderStatus;
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

    private record Sale(LocalDate day, BigDecimal revenue, BigDecimal profit, boolean withoutCost) {
    }

    // [createdAt, amountToCollect, costo de productos, costo del delivery, ítems sin costo]
    private Sale toSale(Object[] row) {
        BigDecimal revenue = decimal(row[1]);
        BigDecimal profit = revenue.subtract(decimal(row[2])).subtract(decimal(row[3]));
        return new Sale(((Instant) row[0]).atZone(zone).toLocalDate(), revenue, profit,
                ((Number) row[4]).longValue() > 0);
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
        for (Sale sale : sales) {
            if (sale.day().isBefore(from) || sale.day().isAfter(to)) continue;
            orders++;
            revenue = revenue.add(sale.revenue());
            profit = profit.add(sale.profit());
            if (sale.withoutCost()) withoutCost++;
        }
        return new Period(from, to, orders, revenue, profit, withoutCost);
    }
}
