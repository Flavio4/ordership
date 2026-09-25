package com.rtz.ordership.dto.webhook;

import java.math.BigDecimal;

/**
 * Datos de un pedido de Shopify ya resueltos a partir del webhook, para crear el pedido en OrderShip.
 *
 * @param shopifyOrderId     id interno de Shopify (se usa para no procesar dos veces el mismo pedido)
 * @param orderName          número que ve la tienda, ej. "#1488"
 * @param adminUrl           link al pedido en Shopify Admin (null si no se conoce la tienda)
 * @param shippingAddressRaw dirección de entrega en texto libre
 * @param amountToCollect    total de Shopify: lo que paga el cliente, con ofertas y extras
 */
public record ShopifyOrderDetails(
        String shopifyOrderId,
        String orderName,
        String adminUrl,
        String shippingAddressRaw,
        BigDecimal amountToCollect) {
}
