package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.enums.Unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respuestas de /reports: pedidos no cancelados creados en el período, en guaraníes. */
public final class ReportResponses {

    private ReportResponses() {
    }

    /**
     * Lo vendido de un producto. revenue = lo que se cobró por él (con los descuentos del pedido repartidos).
     * profit = cobrado − costo, solo de las unidades con costo; complete = false si alguna se vendió sin precio de
     * compra (la ganancia real es menor).
     */
    public record ProductSales(UUID productId, String name, Unit unit, long orders, long quantity,
            BigDecimal revenue, BigDecimal profit, boolean complete) {
    }

    public record CustomerSales(UUID customerId, String fullName, String phone, long orders, BigDecimal revenue,
            Instant lastOrderAt) {
    }

    /** key: el origen (SHOPIFY/MANUAL), la forma de pago o el nombre de la zona; null = sin dato. */
    public record Slice(String key, long orders, BigDecimal revenue) {
    }

    /** Ventas por origen y por zona; por forma de pago, solo lo cobrado (pedidos pagados). */
    public record Breakdown(LocalDate from, LocalDate to, List<Slice> bySource, List<Slice> byPaymentMethod,
            List<Slice> byZone) {
    }
}
