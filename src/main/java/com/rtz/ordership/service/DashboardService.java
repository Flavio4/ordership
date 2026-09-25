package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.DashboardResponse;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.repository.DeliveryAssignmentRepository;
import com.rtz.ordership.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DashboardService {

        private final OrderRepository orderRepository;
        private final DeliveryAssignmentRepository deliveryRepository;

        public DashboardService(OrderRepository orderRepository,
                        DeliveryAssignmentRepository deliveryRepository) {
                this.orderRepository = orderRepository;
                this.deliveryRepository = deliveryRepository;
        }

        @Transactional(readOnly = true)
        public DashboardResponse getDashboard() {
                log.info("Generando dashboard");

                ZoneId zone = ZoneId.systemDefault();
                LocalDate today = LocalDate.now(zone);
                Instant startOfDay = today.atStartOfDay(zone).toInstant();
                Instant endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant();

                LocalDate startOfWeek = today.with(DayOfWeek.MONDAY);
                Instant startOfWeekInstant = startOfWeek.atStartOfDay(zone).toInstant();

                // Pedidos de hoy por estado
                Map<String, Long> ordersByStatus = new LinkedHashMap<>();
                long totalToday = 0;
                List<Object[]> statusCounts = orderRepository.countByStatusGroupedAndCreatedAtBetween(startOfDay,
                                endOfDay);
                for (Object[] row : statusCounts) {
                        OrderStatus status = (OrderStatus) row[0];
                        Long count = (Long) row[1];
                        ordersByStatus.put(status.name(), count);
                        totalToday += count;
                }

                // Revenue
                BigDecimal revenueToday = orderRepository.sumAmountToCollectByCreatedAtBetween(startOfDay, endOfDay,
                                OrderStatus.CANCELLED);
                BigDecimal revenueWeek = orderRepository.sumAmountToCollectByCreatedAtBetween(startOfWeekInstant, endOfDay,
                                OrderStatus.CANCELLED);

                // Contadores globales
                long pending = orderRepository.countByStatus(OrderStatus.PENDING);
                long inTransit = orderRepository.countByStatus(OrderStatus.IN_TRANSIT);

                // Entregas activas por zona
                List<DashboardResponse.ZoneDeliveryCount> deliveriesByZone = deliveryRepository
                                .findAll()
                                .stream()
                                .filter(da -> da.getStatus() == DeliveryStatus.ASSIGNED
                                                || da.getStatus() == DeliveryStatus.IN_TRANSIT)
                                .collect(java.util.stream.Collectors.groupingBy(
                                                da -> da.getZone().getName(),
                                                java.util.stream.Collectors.counting()))
                                .entrySet().stream()
                                .map(e -> new DashboardResponse.ZoneDeliveryCount(e.getKey(), e.getValue()))
                                .toList();

                log.info("Dashboard generado - pedidos hoy: {}, revenue hoy: {}, pendientes: {}", totalToday,
                                revenueToday,
                                pending);

                return new DashboardResponse(
                                ordersByStatus,
                                totalToday,
                                revenueToday,
                                revenueWeek,
                                pending,
                                inTransit,
                                deliveriesByZone);
        }
}
