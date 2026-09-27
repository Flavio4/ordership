package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.DashboardResponse;
import com.rtz.ordership.dto.response.DashboardResponse.DailySales;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    // Sábado: la semana arrancó el lunes 21
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private OrderRepository orderRepository;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        ProductRepository productRepository = mock(ProductRepository.class);
        ShopifyWebhookFailureRepository failureRepository = mock(ShopifyWebhookFailureRepository.class);
        when(productRepository.countByActiveTrueAndNeedsReviewTrue()).thenReturn(4L);
        when(failureRepository.countByResolvedAtIsNull()).thenReturn(1L);
        when(orderRepository.countByStatusIn(List.of(OrderStatus.PENDING))).thenReturn(3L);
        when(orderRepository.countByStatusIn(List.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT))).thenReturn(2L);
        when(orderRepository.countScheduledOn(TODAY, null)).thenReturn(5L);
        when(orderRepository.countScheduledBefore(TODAY, null)).thenReturn(1L);
        service = new DashboardService(orderRepository, productRepository, failureRepository, "America/Asuncion");
    }

    @Test
    void salesAreGroupedByDayInParaguayAndTheWeekStartsOnMonday() {
        when(orderRepository.salesSince(any())).thenReturn(List.of(
                sale("2026-09-20T15:00:00Z", "100000"),   // domingo 20: semana pasada
                sale("2026-09-22T13:00:00Z", "200000"),   // martes 22
                // 23:30 en Paraguay del viernes 25 = 02:30 UTC del sábado: cuenta para el viernes
                sale("2026-09-26T02:30:00Z", "50000"),
                sale("2026-09-26T14:00:00Z", "179000"),
                sale("2026-09-26T20:00:00Z", "60000")));

        DashboardResponse dashboard = service.getDashboard(TODAY);

        assertThat(dashboard.today()).isEqualTo(TODAY);
        assertThat(dashboard.ordersToday()).isEqualTo(2);
        assertThat(dashboard.revenueToday()).isEqualByComparingTo("239000");
        assertThat(dashboard.revenueWeek()).isEqualByComparingTo("489000");
        assertThat(dashboard.lastSevenDays()).extracting(DailySales::date)
                .containsExactly(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22),
                        LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), TODAY);
        assertThat(dashboard.lastSevenDays().get(1).orders()).isZero();
        assertThat(dashboard.lastSevenDays().get(1).revenue()).isEqualByComparingTo("0");
        assertThat(dashboard.lastSevenDays().get(5).revenue()).isEqualByComparingTo("50000");
    }

    @Test
    void theToDoCountsComeFromShopifyOrdersProductsAndFailures() {
        when(orderRepository.salesSince(any())).thenReturn(List.of());

        DashboardResponse dashboard = service.getDashboard(TODAY);

        assertThat(dashboard.pendingOrders()).isEqualTo(3);
        assertThat(dashboard.inDeliveryOrders()).isEqualTo(2);
        assertThat(dashboard.deliveriesToday()).isEqualTo(5);
        assertThat(dashboard.deliveriesOverdue()).isEqualTo(1);
        assertThat(dashboard.productsToReview()).isEqualTo(4);
        assertThat(dashboard.webhookFailures()).isEqualTo(1);
        assertThat(dashboard.ordersToday()).isZero();
        verify(orderRepository).salesSince(Instant.parse("2026-09-20T03:00:00Z"));
    }

    private Object[] sale(String createdAt, String amount) {
        return new Object[] { Instant.parse(createdAt), new BigDecimal(amount) };
    }
}
