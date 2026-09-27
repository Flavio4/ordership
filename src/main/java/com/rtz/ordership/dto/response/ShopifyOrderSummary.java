package com.rtz.ordership.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Lo que se puede leer de un pedido de Shopify guardado, para reconocerlo sin abrir el payload. */
public record ShopifyOrderSummary(
        String orderName,
        String customerName,
        String phone,
        BigDecimal totalPrice,
        String currency,
        List<String> items,
        String shippingAddress) {
}
