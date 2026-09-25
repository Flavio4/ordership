package com.rtz.ordership.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * Datos del pedido en Shopify. Se guardan en la misma tabla orders (columnas shopify_*);
 * en los pedidos manuales todo queda en null y Hibernate carga {@code Order.shopify} como null.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopifyReference {

    // Id interno de Shopify: evita procesar dos veces el mismo pedido
    @Column(name = "shopify_order_id", unique = true)
    private String orderId;

    // Número que ve la tienda (ej. "#1488")
    @Column(name = "shopify_order_name")
    private String orderName;

    // Link al pedido en Shopify Admin, donde está el desglose (descuentos, extras, envío)
    @Column(name = "shopify_admin_url", columnDefinition = "TEXT")
    private String adminUrl;
}
