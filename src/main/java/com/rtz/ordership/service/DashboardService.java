package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.DashboardResponse;
import com.rtz.ordership.dto.response.DashboardResponse.DailySales;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        return getDashboard(LocalDate.now(zone));
    }

    DashboardResponse getDashboard(LocalDate today) {
        LocalDate firstDay = today.minusDays(DAYS - 1);
        Instant from = firstDay.atStartOfDay(zone).toInstant();

        Map<LocalDate, long[]> ordersByDay = new TreeMap<>();
        Map<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        for (LocalDate day = firstDay; !day.isAfter(today); day = day.plusDays(1)) {
            ordersByDay.put(day, new long[1]);
            revenueByDay.put(day, BigDecimal.ZERO);
        }
        for (Object[] sale : orderRepository.salesSince(from)) {
            LocalDate day = ((Instant) sale[0]).atZone(zone).toLocalDate();
            if (ordersByDay.containsKey(day)) {
                ordersByDay.get(day)[0]++;
                revenueByDay.merge(day, (BigDecimal) sale[1], BigDecimal::add);
            }
        }

        List<DailySales> lastSevenDays = ordersByDay.keySet().stream()
                .map(day -> new DailySales(day, ordersByDay.get(day)[0], revenueByDay.get(day)))
                .toList();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        BigDecimal revenueWeek = lastSevenDays.stream()
                .filter(sales -> !sales.date().isBefore(monday))
                .map(DailySales::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DashboardResponse dashboard = new DashboardResponse(
                today,
                ordersByDay.get(today)[0],
                revenueByDay.get(today),
                revenueWeek,
                orderRepository.countByStatusIn(List.of(OrderStatus.PENDING)),
                orderRepository.countByStatusIn(List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT)),
                orderRepository.countScheduledOn(today, null),
                orderRepository.countScheduledBefore(today, null),
                productRepository.countByActiveTrueAndNeedsReviewTrue(),
                failureRepository.countByResolvedAtIsNull(),
                lastSevenDays);
        log.info("Dashboard del {} - pedidos hoy: {}, ventas hoy: {}, pendientes: {}",
                today, dashboard.ordersToday(), dashboard.revenueToday(), dashboard.pendingOrders());
        return dashboard;
    }
}
