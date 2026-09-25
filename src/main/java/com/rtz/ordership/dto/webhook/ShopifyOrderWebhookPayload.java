package com.rtz.ordership.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopifyOrderWebhookPayload(
        Long id,
        // Número de pedido que ve la tienda, ej. "#1488"
        String name,
        String email,
        String phone,
        String currency,
        // Total que paga el cliente (con ofertas por cantidad y extras incluidos)
        @JsonProperty("total_price") BigDecimal totalPrice,
        ShopifyCustomer customer,
        @JsonProperty("shipping_address") ShopifyAddress shippingAddress,
        @JsonProperty("line_items") List<ShopifyLineItem> lineItems,
        // "Información adicional" del pedido: acá guarda el formulario de Releasit lo que escribe el comprador
        @JsonProperty("note_attributes") List<NoteAttribute> noteAttributes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoteAttribute(
            String name,
            String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShopifyCustomer(
            Long id,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email,
            String phone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShopifyAddress(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String address1,
            String address2,
            String city,
            String province,
            String zip,
            String country,
            String phone) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShopifyLineItem(
            Long id,
            String title,
            @JsonProperty("variant_title") String variantTitle,
            String sku,
            Integer quantity,
            BigDecimal price) {
    }
}
