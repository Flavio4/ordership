package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ReportResponses.Breakdown;
import com.rtz.ordership.dto.response.ReportResponses.CustomerSales;
import com.rtz.ordership.dto.response.ReportResponses.ProductSales;
import com.rtz.ordership.dto.response.ReportResponses.Slice;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.PaymentMethod;
import com.rtz.ordership.entity.enums.Unit;
import com.rtz.ordership.repository.ReportRepository;
import com.rtz.ordership.service.ReportService.ProductOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    // Sábado 26 al mediodía en Paraguay
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T15:00:00Z"), ZoneId.of("America/Asuncion"));

    private ReportRepository reportRepository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        service = new ReportService(reportRepository, "America/Asuncion");
    }

    @Test
    void productsAreOrderedByWhatWasAskedAndProfitOnlyCountsUnitsWithCost() {
        when(reportRepository.productSales(any(), any())).thenReturn(rows(
                product("Cúrcuma", 3, 10, "1790000.4", "1790000.4", "1200000", 0),
                // 2 unidades sin precio de compra: la ganancia queda incompleta
                product("Té nuevo", 5, 12, "600000", "400000", "250000", 2),
                product("Miel", 8, 20, "1000000", "1000000", "500000", 0)));

        Page<ProductSales> byProfit = service.productSales(null, null, ProductOrder.PROFIT, PageRequest.of(0, 20), CLOCK);
        assertThat(byProfit.getContent()).extracting(ProductSales::name).containsExactly("Cúrcuma", "Miel", "Té nuevo");
        ProductSales curcuma = byProfit.getContent().getFirst();
        assertThat(curcuma.revenue()).isEqualByComparingTo("1790000");
        assertThat(curcuma.profit()).isEqualByComparingTo("590000");
        assertThat(curcuma.complete()).isTrue();
        ProductSales te = byProfit.getContent().get(2);
        assertThat(te.profit()).isEqualByComparingTo("150000");
        assertThat(te.complete()).isFalse();

        assertThat(service.productSales(null, null, ProductOrder.QUANTITY, PageRequest.of(0, 20), CLOCK).getContent())
                .extracting(ProductSales::name).containsExactly("Miel", "Té nuevo", "Cúrcuma");
        assertThat(service.productSales(null, null, ProductOrder.REVENUE, PageRequest.of(0, 20), CLOCK).getContent())
                .extracting(ProductSales::name).containsExactly("Cúrcuma", "Miel", "Té nuevo");
    }

    @Test
    void theListIsPagedAfterSorting() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            rows.add(product("Producto " + i, 1, i, i + "000", i + "000", "0", 0));
        }
        when(reportRepository.productSales(any(), any())).thenReturn(rows);

        Page<ProductSales> second = service.productSales(null, null, ProductOrder.QUANTITY, PageRequest.of(1, 20), CLOCK);

        assertThat(second.getTotalElements()).isEqualTo(25);
        assertThat(second.isLast()).isTrue();
        assertThat(second.getContent()).hasSize(5);
        assertThat(second.getContent().getFirst().quantity()).isEqualTo(5);
    }

    @Test
    void thePeriodIsCountedInParaguayAndWithoutToItEndsToday() {
        when(reportRepository.productSales(any(), any())).thenReturn(List.of());

        service.productSales(LocalDate.of(2026, 9, 1), null, ProductOrder.PROFIT, Pageable.unpaged(), CLOCK);

        // Medianoche en Paraguay (UTC-3): del 1/9 hasta el final del sábado 26
        verify(reportRepository).productSales(Instant.parse("2026-09-01T03:00:00Z"),
                Instant.parse("2026-09-27T03:00:00Z"));
        assertThatThrownBy(() -> service.productSales(LocalDate.of(2026, 9, 27), null, ProductOrder.PROFIT,
                Pageable.unpaged(), CLOCK)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customersWhoBoughtMoreComeFirst() {
        when(reportRepository.customerSales(any(), any())).thenReturn(rows(
                customer("Ana", 2, "300000"),
                customer("Beto", 5, "900000")));

        assertThat(service.customerSales(null, null, PageRequest.of(0, 20), CLOCK).getContent())
                .extracting(CustomerSales::fullName).containsExactly("Beto", "Ana");
    }

    @Test
    void inactiveCustomersAreTheOnesWithoutOrdersInTheLastDays() {
        when(reportRepository.customersWithoutOrdersSince(any())).thenReturn(rows(customer("Ana", 2, "300000")));

        service.inactiveCustomers(60, PageRequest.of(0, 20), CLOCK);

        verify(reportRepository).customersWithoutOrdersSince(Instant.parse("2026-07-28T15:00:00Z"));
        assertThatThrownBy(() -> service.inactiveCustomers(0, PageRequest.of(0, 20), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theBreakdownKeepsTheKeysAndPutsTheBiggestFirst() {
        when(reportRepository.salesBySource(any(), any())).thenReturn(rows(
                new Object[] {OrderSource.MANUAL, 3L, new BigDecimal("400000")},
                new Object[] {OrderSource.SHOPIFY, 10L, new BigDecimal("2000000")}));
        when(reportRepository.collectedByPaymentMethod(any(), any())).thenReturn(rows(
                new Object[] {null, 1L, new BigDecimal("100000")},
                new Object[] {PaymentMethod.CASH, 6L, new BigDecimal("900000")}));
        when(reportRepository.salesByZone(any(), any())).thenReturn(rows(
                new Object[] {"Asunción", 7L, new BigDecimal("1500000")}));

        Breakdown breakdown = service.breakdown(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), CLOCK);

        assertThat(breakdown.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(breakdown.to()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(breakdown.bySource()).extracting(Slice::key).containsExactly("SHOPIFY", "MANUAL");
        assertThat(breakdown.byPaymentMethod()).extracting(Slice::key).containsExactly("CASH", null);
        assertThat(breakdown.byZone()).singleElement().satisfies(zone -> assertThat(zone.orders()).isEqualTo(7));
    }

    private static List<Object[]> rows(Object[]... rows) {
        return List.of(rows);
    }

    private static Object[] product(String name, long orders, long quantity, String revenue, String costedRevenue,
            String cost, long withoutCost) {
        return new Object[] {UUID.randomUUID(), name, Unit.UNID, orders, quantity, new BigDecimal(revenue),
                new BigDecimal(costedRevenue), new BigDecimal(cost), withoutCost};
    }

    private static Object[] customer(String name, long orders, String revenue) {
        return new Object[] {UUID.randomUUID(), name, "+595981000111", orders, new BigDecimal(revenue),
                Instant.parse("2026-09-20T15:00:00Z")};
    }
}
