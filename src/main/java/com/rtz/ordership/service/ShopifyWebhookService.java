package com.rtz.ordership.service;

import tools.jackson.databind.ObjectMapper;
import com.rtz.ordership.dto.request.OrderItemRequest;
import com.rtz.ordership.dto.webhook.ShopifyOrderDetails;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload;
import com.rtz.ordership.dto.webhook.ShopifyOrderWebhookPayload.ShopifyLineItem;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.Unit;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.util.PhoneNumbers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Crea pedidos a partir de los webhooks de Shopify. La venta ya ocurrió (el cliente pagó), así que el pedido
 * se crea siempre que sea posible: un SKU desconocido crea el producto y la falta de stock no lo bloquea.
 * Cuando igual no se puede crear, distingue dos tipos de error:
 * - Errores de datos (sin teléfono, ítem sin SKU, moneda no soportada...): reintentar no los arregla, así que
 *   se guarda el payload como fallo y el webhook responde 200. Si respondiera error, Shopify reintentaría
 *   durante horas y podría terminar desactivando el webhook.
 * - Errores técnicos (base caída, etc.): se propagan para que el webhook responda 500 y Shopify reintente.
 */
@Slf4j
@Service
public class ShopifyWebhookService {

    private static final Pattern SHOP_DOMAIN = Pattern.compile("[a-z0-9][a-z0-9-]*\\.myshopify\\.com");

    private final OrderService orderService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ShopifyWebhookFailureService failureService;
    private final ShopifyOrderReader reader;
    private final TransactionTemplate transactionTemplate;
    // Ítems que no son productos (ej. el extra "Envio Prioritario y Garantia Extendida" del formulario):
    // no se crean en el catálogo ni mueven stock. Su importe igual queda en amountToCollect (total de Shopify)
    private final Set<String> ignoredLineItems;

    public ShopifyWebhookService(OrderService orderService,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            ShopifyWebhookFailureService failureService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Value("${app.shopify.ignored-line-items:}") List<String> ignoredLineItems) {
        this.orderService = orderService;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.failureService = failureService;
        this.reader = new ShopifyOrderReader(objectMapper);
        this.transactionTemplate = transactionTemplate;
        this.ignoredLineItems = ignoredLineItems.stream()
                .filter(ShopifyOrderReader::notBlank)
                .map(ShopifyOrderReader::normalizeKey)
                .collect(Collectors.toSet());
    }

    /**
     * @param shopDomain header X-Shopify-Shop-Domain (ej. "mitienda.myshopify.com"), para armar el link al pedido
     */
    public void receiveOrderCreated(String rawBody, String shopDomain) {
        try {
            createOrder(rawBody, shopDomain);
        } catch (RuntimeException e) {
            if (!isDataError(e)) {
                throw e;
            }
            String shopifyOrderId = extractShopifyOrderId(rawBody);
            failureService.recordFailure(shopifyOrderId, rawBody, e.getMessage(),
                    shopifyOrderId == null ? null : adminUrl(shopDomain, shopifyOrderId));
        }
    }

    // La creación corre en su propia transacción: si falla se revierte entera (sin clientes a medias)
    // y el fallo se registra después, fuera de ella.
    private void createOrder(String rawBody, String shopDomain) {
        transactionTemplate.executeWithoutResult(status -> processOrderCreated(rawBody, shopDomain));
    }

    private boolean isDataError(RuntimeException e) {
        return e instanceof IllegalStateException
                || e instanceof IllegalArgumentException
                || e instanceof ResourceNotFoundException;
    }

    private String extractShopifyOrderId(String rawBody) {
        try {
            Long id = reader.parse(rawBody).id();
            return id == null ? null : String.valueOf(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void processOrderCreated(String rawBody, String shopDomain) {
        ShopifyOrderWebhookPayload payload = reader.parse(rawBody);
        String shopifyOrderId = String.valueOf(payload.id());
        log.info("Procesando webhook de Shopify - pedido: {}", shopifyOrderId);

        Map<String, String> formFields = reader.formFields(payload);

        String phone = PhoneNumbers.normalize(reader.phone(payload, formFields));
        if (phone == null || phone.isBlank()) {
            throw new IllegalStateException(
                    "El pedido de Shopify " + shopifyOrderId + " no trae un teléfono de contacto");
        }

        Customer customer = customerRepository.findByPhone(phone)
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .fullName(reader.customerName(payload, formFields))
                        .phone(phone)
                        .email(reader.email(payload))
                        .build()));

        List<OrderItemRequest> items = resolveItems(payload, shopifyOrderId);
        ShopifyOrderDetails details = new ShopifyOrderDetails(
                shopifyOrderId,
                payload.name(),
                adminUrl(shopDomain, shopifyOrderId),
                reader.shippingAddress(payload.shippingAddress(), formFields),
                payload.totalPrice());

        orderService.createOrderFromShopify(customer, details, items);
    }

    /**
     * Link al pedido en Shopify Admin: https://admin.shopify.com/store/{tienda}/orders/{id}.
     * El header del dominio no está cubierto por la firma HMAC, así que solo se acepta un dominio *.myshopify.com.
     */
    private String adminUrl(String shopDomain, String shopifyOrderId) {
        if (shopDomain == null || !SHOP_DOMAIN.matcher(shopDomain.trim()).matches()) {
            return null;
        }
        String store = shopDomain.trim().substring(0, shopDomain.trim().indexOf(".myshopify.com"));
        return "https://admin.shopify.com/store/" + store + "/orders/" + shopifyOrderId;
    }

    private List<OrderItemRequest> resolveItems(ShopifyOrderWebhookPayload payload, String shopifyOrderId) {
        if (payload.lineItems() == null || payload.lineItems().isEmpty()) {
            throw new IllegalStateException("El pedido de Shopify " + shopifyOrderId + " no tiene ítems");
        }

        Currency currency = resolveCurrency(payload.currency(), shopifyOrderId);
        List<OrderItemRequest> items = payload.lineItems().stream()
                .filter(li -> !isIgnored(li, shopifyOrderId))
                .map(li -> resolveItem(li, currency, shopifyOrderId))
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            throw new IllegalStateException("El pedido de Shopify " + shopifyOrderId
                    + " no tiene productos (solo extras ignorados)");
        }
        return items;
    }

    private boolean isIgnored(ShopifyLineItem lineItem, String shopifyOrderId) {
        if (lineItem.title() == null || !ignoredLineItems.contains(ShopifyOrderReader.normalizeKey(lineItem.title()))) {
            return false;
        }
        log.info("Ítem '{}' del pedido Shopify {} ignorado (no es un producto); su importe queda en el monto a cobrar",
                lineItem.title(), shopifyOrderId);
        return true;
    }

    private OrderItemRequest resolveItem(ShopifyLineItem lineItem, Currency currency, String shopifyOrderId) {
        if (lineItem.sku() == null || lineItem.sku().isBlank()) {
            throw new IllegalStateException(
                    "El ítem '" + lineItem.title() + "' del pedido Shopify " + shopifyOrderId + " no tiene SKU");
        }
        Product product = productRepository.findByShopifySku(lineItem.sku())
                .orElseGet(() -> linkOrCreateProduct(lineItem, currency));
        return new OrderItemRequest(product.getId(), lineItem.quantity());
    }

    /**
     * SKU desconocido: si hay un producto con el mismo nombre y sin SKU, se le asigna el SKU (evita duplicados);
     * si no, se crea el producto con los datos del pedido, marcado para completar el precio de compra.
     */
    private Product linkOrCreateProduct(ShopifyLineItem lineItem, Currency currency) {
        String name = reader.productName(lineItem);

        Optional<Product> sameNameWithoutSku = productRepository.findFirstByNameIgnoreCaseAndShopifySkuIsNull(name);
        if (sameNameWithoutSku.isPresent()) {
            Product product = sameNameWithoutSku.get();
            product.setShopifySku(lineItem.sku());
            log.info("SKU '{}' asignado automáticamente al producto existente '{}'", lineItem.sku(), product.getName());
            return productRepository.save(product);
        }

        // Nombre ocupado por otro producto con distinto SKU: se agrega el SKU para distinguirlos
        if (productRepository.existsByName(name)) {
            name = name + " (" + lineItem.sku() + ")";
        }

        Product product = productRepository.save(Product.builder()
                .name(name)
                .shopifySku(lineItem.sku())
                .salePrice(lineItem.price() != null ? lineItem.price() : BigDecimal.ZERO)
                .purchasePrice(BigDecimal.ZERO)
                .currency(currency)
                .unit(Unit.UNID)
                .stock(0)
                .needsReview(true)
                .build());
        log.info("Producto '{}' creado automáticamente desde Shopify (SKU '{}'), falta completar precio de compra",
                product.getName(), lineItem.sku());
        return product;
    }

    private Currency resolveCurrency(String currency, String shopifyOrderId) {
        try {
            return Currency.valueOf(currency.trim().toUpperCase());
        } catch (RuntimeException e) {
            throw new IllegalStateException("El pedido de Shopify " + shopifyOrderId
                    + " tiene una moneda no soportada: " + currency);
        }
    }
}
