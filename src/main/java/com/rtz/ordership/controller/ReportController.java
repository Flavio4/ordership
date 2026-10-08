package com.rtz.ordership.controller;

import com.rtz.ordership.dto.response.ReportResponses.Breakdown;
import com.rtz.ordership.dto.response.ReportResponses.CustomerSales;
import com.rtz.ordership.dto.response.ReportResponses.ProductSales;
import com.rtz.ordership.service.ReportService;
import com.rtz.ordership.service.ReportService.ProductOrder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
@Tag(name = "Reportes", description = "Productos, clientes y ventas de un período")
public class ReportController {

    private static final String PERIOD = "Pedidos no cancelados creados entre from y to (días en hora de Paraguay, "
            + "inclusive). Sin from: desde el primer pedido. Sin to: hasta hoy. ";

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/products")
    @Operation(summary = "Productos vendidos", description = PERIOD + "Una fila por producto, ordenada por orderBy "
            + "(PROFIT, REVENUE o QUANTITY, de mayor a menor). revenue = lo cobrado por el producto, con los descuentos "
            + "del pedido repartidos; profit solo de las unidades con precio de compra (complete = false si faltó alguno)")
    public ResponseEntity<Page<ProductSales>> productSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "PROFIT") ProductOrder orderBy,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reportService.productSales(from, to, orderBy, pageable));
    }

    @GetMapping("/customers")
    @Operation(summary = "Clientes que más compraron", description = PERIOD + "Una fila por cliente, de mayor a menor "
            + "por lo vendido (amountToCollect)")
    public ResponseEntity<Page<CustomerSales>> customerSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reportService.customerSales(from, to, pageable));
    }

    @GetMapping("/inactive-customers")
    @Operation(summary = "Clientes sin comprar", description = "Clientes cuyo último pedido no cancelado tiene más de "
            + "days días, con todo lo que compraron; los que más compraron primero")
    public ResponseEntity<Page<CustomerSales>> inactiveCustomers(
            @RequestParam(defaultValue = "60") int days,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reportService.inactiveCustomers(days, pageable));
    }

    @GetMapping("/breakdown")
    @Operation(summary = "Ventas por origen, forma de pago y zona", description = PERIOD + "Por forma de pago, solo "
            + "lo cobrado (pedidos pagados). key null = sin forma de pago o sin zona")
    public ResponseEntity<Breakdown> breakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.breakdown(from, to));
    }
}
