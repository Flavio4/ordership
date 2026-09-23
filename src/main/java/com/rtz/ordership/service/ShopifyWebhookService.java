package com.rtz.ordership.service;

import tools.jackson.databind.ObjectMapper;
import com.rtz.ordership.dto.request.OrderItemRequest;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload.ShopifyAddress;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload.ShopifyLineItem;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShopifyWebhookService {

    private final OrderService orderService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public ShopifyWebhookService(OrderService orderService,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processOrderCreated(String rawBody) {
        ShopifyOrderWebhookPayload payload = parsePayload(rawBody);
        String shopifyOrderId = String.valueOf(payload.id());
        log.info("Procesando webhook de Shopify - pedido: {}", shopifyOrderId);

        String phone = resolvePhone(payload);
        if (phone == null || phone.isBlank()) {
            throw new IllegalStateException(
                    "El pedido de Shopify " + shopifyOrderId + " no trae un teléfono de contacto");
        }

        Customer customer = customerRepository.findByPhone(phone)
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .fullName(resolveCustomerName(payload))
                        .phone(phone)
                        .email(resolveEmail(payload))
                        .build()));

        List<OrderItemRequest> items = resolveItems(payload, shopifyOrderId);
        String shippingAddressRaw = resolveShippingAddress(payload.shippingAddress());

        orderService.createOrderFromShopify(customer, shippingAddressRaw, shopifyOrderId, null, items);
    }

    private ShopifyOrderWebhookPayload parsePayload(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, ShopifyOrderWebhookPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Payload de Shopify inválido: " + e.getMessage(), e);
        }
    }

    private List<OrderItemRequest> resolveItems(ShopifyOrderWebhookPayload payload, String shopifyOrderId) {
        if (payload.lineItems() == null || payload.lineItems().isEmpty()) {
            throw new IllegalStateException("El pedido de Shopify " + shopifyOrderId + " no tiene ítems");
        }

        return payload.lineItems().stream()
                .map(li -> resolveItem(li, shopifyOrderId))
                .collect(Collectors.toList());
    }

    private OrderItemRequest resolveItem(ShopifyLineItem lineItem, String shopifyOrderId) {
        if (lineItem.sku() == null || lineItem.sku().isBlank()) {
            throw new IllegalStateException(
                    "El ítem '" + lineItem.title() + "' del pedido Shopify " + shopifyOrderId + " no tiene SKU");
        }
        Product product = productRepository.findByShopifySku(lineItem.sku())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró ningún producto con shopifySku='" + lineItem.sku()
                                + "' (" + lineItem.title() + "). Cargá el SKU en el producto correspondiente."));
        return new OrderItemRequest(product.getId(), lineItem.quantity());
    }

    private String resolvePhone(ShopifyOrderWebhookPayload payload) {
        if (payload.shippingAddress() != null && notBlank(payload.shippingAddress().phone())) {
            return payload.shippingAddress().phone();
        }
        if (payload.customer() != null && notBlank(payload.customer().phone())) {
            return payload.customer().phone();
        }
        return payload.phone();
    }

    private String resolveCustomerName(ShopifyOrderWebhookPayload payload) {
        if (payload.customer() != null
                && (notBlank(payload.customer().firstName()) || notBlank(payload.customer().lastName()))) {
            return joinNames(payload.customer().firstName(), payload.customer().lastName());
        }
        if (payload.shippingAddress() != null
                && (notBlank(payload.shippingAddress().firstName()) || notBlank(payload.shippingAddress().lastName()))) {
            return joinNames(payload.shippingAddress().firstName(), payload.shippingAddress().lastName());
        }
        return "Cliente Shopify #" + payload.id();
    }

    private String resolveEmail(ShopifyOrderWebhookPayload payload) {
        if (payload.customer() != null && notBlank(payload.customer().email())) {
            return payload.customer().email();
        }
        return payload.email();
    }

    private String resolveShippingAddress(ShopifyAddress address) {
        if (address == null) {
            return null;
        }
        return List.of(
                orEmpty(address.address1()),
                orEmpty(address.address2()),
                orEmpty(address.city()),
                orEmpty(address.province()),
                orEmpty(address.zip()),
                orEmpty(address.country()))
                .stream()
                .filter(this::notBlank)
                .collect(Collectors.joining(", "));
    }

    private String joinNames(String first, String last) {
        return (orEmpty(first) + " " + orEmpty(last)).trim();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
