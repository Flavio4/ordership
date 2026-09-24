package com.rtz.ordership.dto.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShopifyOrderWebhookPayload(
        Long id,
        String email,
        String phone,
        String currency,
        ShopifyCustomer customer,
        @JsonProperty("shipping_address") ShopifyAddress shippingAddress,
        @JsonProperty("line_items") List<ShopifyLineItem> lineItems) {

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
