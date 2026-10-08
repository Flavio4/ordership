package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ReportResponses.Breakdown;
import com.rtz.ordership.dto.response.ReportResponses.CustomerSales;
import com.rtz.ordership.dto.response.ReportResponses.ProductSales;
import com.rtz.ordership.dto.response.ReportResponses.Slice;
import com.rtz.ordership.entity.enums.Unit;
import com.rtz.ordership.repository.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Reportes de un período [from, to] (días en hora del negocio, como el resumen de Inicio). Sin from: desde el primer
 * pedido; sin to: hasta hoy. La base suma por producto o cliente; el orden y la página se arman acá, porque son pocas
 * filas (una por producto o cliente que compró).
 */
@Slf4j
@Service
public class ReportService {

    public enum ProductOrder { PROFIT, REVENUE, QUANTITY }

    private final ReportRepository reportRepository;
    private final ZoneId zone;

    public ReportService(ReportRepository reportRepository,
            @Value("${app.timezone:America/Asuncion}") String timezone) {
        this.reportRepository = reportRepository;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public Page<ProductSales> productSales(LocalDate from, LocalDate to, ProductOrder orderBy, Pageable pageable) {
        return productSales(from, to, orderBy, pageable, Clock.system(zone));
    }

    Page<ProductSales> productSales(LocalDate from, LocalDate to, ProductOrder orderBy, Pageable pageable,
            Clock clock) {
        Range range = range(from, to, clock);
        log.info("Reporte de productos del {} al {} por {}", range.from(), range.to(), orderBy);
        Comparator<ProductSales> order = switch (orderBy) {
            case PROFIT -> Comparator.comparing(ProductSales::profit);
            case REVENUE -> Comparator.comparing(ProductSales::revenue);
            case QUANTITY -> Comparator.comparingLong(ProductSales::quantity);
        };
        List<ProductSales> rows = reportRepository.productSales(range.start(), range.before()).stream()
                .map(ReportService::toProductSales)
                .sorted(order.reversed().thenComparing(ProductSales::name))
                .toList();
        return page(rows, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CustomerSales> customerSales(LocalDate from, LocalDate to, Pageable pageable) {
        return customerSales(from, to, pageable, Clock.system(zone));
    }

    Page<CustomerSales> customerSales(LocalDate from, LocalDate to, Pageable pageable, Clock clock) {
        Range range = range(from, to, clock);
        log.info("Reporte de clientes del {} al {}", range.from(), range.to());
        return page(byRevenue(reportRepository.customerSales(range.start(), range.before())), pageable);
    }

    /** Clientes que no compran hace más de {@code days} días, los que más compraron primero: para volver a escribirles. */
    @Transactional(readOnly = true)
    public Page<CustomerSales> inactiveCustomers(int days, Pageable pageable) {
        return inactiveCustomers(days, pageable, Clock.system(zone));
    }

    Page<CustomerSales> inactiveCustomers(int days, Pageable pageable, Clock clock) {
        if (days < 1) {
            throw new IllegalArgumentException("La cantidad de días tiene que ser mayor a 0");
        }
        Instant since = clock.instant().minus(Duration.ofDays(days));
        return page(byRevenue(reportRepository.customersWithoutOrdersSince(since)), pageable);
    }

    @Transactional(readOnly = true)
    public Breakdown breakdown(LocalDate from, LocalDate to) {
        return breakdown(from, to, Clock.system(zone));
    }

    Breakdown breakdown(LocalDate from, LocalDate to, Clock clock) {
        Range range = range(from, to, clock);
        return new Breakdown(range.from(), range.to(),
                slices(reportRepository.salesBySource(range.start(), range.before())),
                slices(reportRepository.collectedByPaymentMethod(range.start(), range.before())),
                slices(reportRepository.salesByZone(range.start(), range.before())));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private record Range(LocalDate from, LocalDate to, Instant start, Instant before) {
    }

    private Range range(LocalDate from, LocalDate to, Clock clock) {
        LocalDate until = to != null ? to : LocalDate.now(clock);
        if (from != null && from.isAfter(until)) {
            throw new IllegalArgumentException("La fecha \"desde\" no puede ser posterior a \"hasta\"");
        }
        Instant start = from != null ? from.atStartOfDay(zone).toInstant() : Instant.EPOCH;
        return new Range(from, until, start, until.plusDays(1).atStartOfDay(zone).toInstant());
    }

    // [id, nombre, unidad, pedidos, unidades, cobrado, cobrado de las unidades con costo, costo, unidades sin costo]
    static ProductSales toProductSales(Object[] row) {
        BigDecimal revenue = money(row[5]);
        BigDecimal profit = money(decimal(row[6]).subtract(decimal(row[7])));
        return new ProductSales((UUID) row[0], (String) row[1], (Unit) row[2], count(row[3]), count(row[4]),
                revenue, profit, count(row[8]) == 0);
    }

    private static List<CustomerSales> byRevenue(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new CustomerSales((UUID) row[0], (String) row[1], (String) row[2], count(row[3]),
                        money(row[4]), (Instant) row[5]))
                .sorted(Comparator.comparing(CustomerSales::revenue).reversed()
                        .thenComparing(CustomerSales::fullName))
                .toList();
    }

    private static List<Slice> slices(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new Slice(row[0] == null ? null : row[0].toString(), count(row[1]), money(row[2])))
                .sorted(Comparator.comparing(Slice::revenue).reversed())
                .toList();
    }

    private static <T> Page<T> page(List<T> rows, Pageable pageable) {
        Pageable request = pageable.isPaged() ? pageable : PageRequest.of(0, Math.max(rows.size(), 1));
        int start = (int) Math.min(request.getOffset(), rows.size());
        int end = Math.min(start + request.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(start, end), PageRequest.of(request.getPageNumber(), request.getPageSize()),
                rows.size());
    }

    private static long count(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    // Guaraníes, sin decimales
    private static BigDecimal money(Object value) {
        return decimal(value).setScale(0, RoundingMode.HALF_UP);
    }
}
