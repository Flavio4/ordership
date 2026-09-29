package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.DashboardResponse;
import com.rtz.ordership.dto.response.DashboardResponse.DailySales;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.PaymentStatus;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    // Sábado 26 al mediodía en Paraguay: la semana arrancó el lunes 21
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T15:00:00Z"), ZoneId.of("America/Asuncion"));

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
        when(orderRepository.countDeliveredWithoutCostSince(Instant.parse("2026-08-27T15:00:00Z"))).thenReturn(6L);
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

        DashboardResponse dashboard = service.getDashboard(CLOCK);

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
    void theProfitSubtractsProductsAndDeliveryAndCountsOrdersWithoutCost() {
        when(orderRepository.salesSince(any())).thenReturn(List.of(
                sale("2026-09-26T14:00:00Z", "179000", "100000", "20000", 0),
                sale("2026-09-26T16:00:00Z", "60000", "10000", "0", 1),
                sale("2026-09-22T13:00:00Z", "200000", "150000", "0", 0)));

        DashboardResponse dashboard = service.getDashboard(CLOCK);

        assertThat(dashboard.todayTotals().revenue()).isEqualByComparingTo("239000");
        assertThat(dashboard.todayTotals().profit()).isEqualByComparingTo("109000");
        assertThat(dashboard.todayTotals().ordersWithoutCost()).isEqualTo(1);
        assertThat(dashboard.week().profit()).isEqualByComparingTo("159000");
        assertThat(dashboard.lastSevenDays().get(6).profit()).isEqualByComparingTo("109000");
        assertThat(dashboard.lastSevenDays().get(2).profit()).isEqualByComparingTo("50000");
    }

    @Test
    void theCollectedPartOfTheSalesOnlyCountsPaidOrders() {
        when(orderRepository.salesSince(any())).thenReturn(List.of(
                sale("2026-09-26T14:00:00Z", "179000", "100000", "20000", 0, PaymentStatus.PAID),
                sale("2026-09-26T16:00:00Z", "60000", "10000", "0", 1, PaymentStatus.PARTIAL),
                sale("2026-09-26T17:00:00Z", "80000", "30000", "0", 1, PaymentStatus.PAID),
                sale("2026-09-22T13:00:00Z", "200000", "150000", "0", 0, PaymentStatus.UNPAID)));

        DashboardResponse dashboard = service.getDashboard(CLOCK);

        assertThat(dashboard.todayTotals().orders()).isEqualTo(3);
        assertThat(dashboard.todayTotals().revenue()).isEqualByComparingTo("319000");
        assertThat(dashboard.todayTotals().collectedOrders()).isEqualTo(2);
        assertThat(dashboard.todayTotals().collectedRevenue()).isEqualByComparingTo("259000");
        assertThat(dashboard.todayTotals().collectedProfit()).isEqualByComparingTo("109000");
        assertThat(dashboard.todayTotals().collectedWithoutCost()).isEqualTo(1);
        assertThat(dashboard.week().collectedRevenue()).isEqualByComparingTo("259000");
        assertThat(dashboard.month().collectedOrders()).isEqualTo(2);
    }

    @Test
    void theMonthIsComparedWithTheSameStretchOfThePreviousMonth() {
        when(orderRepository.salesSince(any())).thenReturn(List.of(
                sale("2026-09-02T15:00:00Z", "300000", "100000", "0", 0),
                sale("2026-08-10T15:00:00Z", "100000", "40000", "0", 0),
                // 27 de agosto: ya pasa el día 26, no entra en la comparación
                sale("2026-08-27T15:00:00Z", "500000", "100000", "0", 0)));

        DashboardResponse dashboard = service.getDashboard(CLOCK);

        assertThat(dashboard.month().from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(dashboard.month().revenue()).isEqualByComparingTo("300000");
        assertThat(dashboard.previousMonth().from()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(dashboard.previousMonth().to()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(dashboard.previousMonth().revenue()).isEqualByComparingTo("100000");
        assertThat(dashboard.previousMonth().profit()).isEqualByComparingTo("60000");
    }

    @Test
    void theToDoCountsComeFromOrdersProductsAndFailures() {
        when(orderRepository.salesSince(any())).thenReturn(List.of());

        DashboardResponse dashboard = service.getDashboard(CLOCK);

        assertThat(dashboard.pendingOrders()).isEqualTo(3);
        assertThat(dashboard.inDeliveryOrders()).isEqualTo(2);
        assertThat(dashboard.deliveriesToday()).isEqualTo(5);
        assertThat(dashboard.deliveriesOverdue()).isEqualTo(1);
        assertThat(dashboard.productsToReview()).isEqualTo(4);
        assertThat(dashboard.webhookFailures()).isEqualTo(1);
        assertThat(dashboard.deliveredWithoutCost()).isEqualTo(6);
        assertThat(dashboard.ordersToday()).isZero();
        // Desde el 1 del mes anterior (lo que queda antes: los últimos 7 días caen dentro)
        verify(orderRepository).salesSince(Instant.parse("2026-08-01T03:00:00Z"));
    }

    private Object[] sale(String createdAt, String amount) {
        return sale(createdAt, amount, "0", "0", 0);
    }

    private Object[] sale(String createdAt, String amount, String productCost, String deliveryCost, long withoutCost) {
        return sale(createdAt, amount, productCost, deliveryCost, withoutCost, PaymentStatus.UNPAID);
    }

    private Object[] sale(String createdAt, String amount, String productCost, String deliveryCost, long withoutCost,
            PaymentStatus paymentStatus) {
        return new Object[] { Instant.parse(createdAt), new BigDecimal(amount), new BigDecimal(productCost),
                new BigDecimal(deliveryCost), withoutCost, paymentStatus };
    }
}
