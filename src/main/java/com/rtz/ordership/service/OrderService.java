package com.rtz.ordership.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rtz.ordership.dto.request.OrderAddressUpdateRequest;
import com.rtz.ordership.dto.request.OrderDiscountRequest;
import com.rtz.ordership.dto.request.OrderItemRequest;
import com.rtz.ordership.dto.request.OrderItemsUpdateRequest;
import com.rtz.ordership.dto.request.OrderRequest;
import com.rtz.ordership.dto.request.OrderStatusUpdateRequest;
import com.rtz.ordership.dto.request.PaymentStatusUpdateRequest;
import com.rtz.ordership.dto.response.AgendaSummaryResponse;
import com.rtz.ordership.dto.response.OrderResponse;
import com.rtz.ordership.dto.webhook.ShopifyOrderDetails;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.CustomerAddress;
import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.OrderDiscount;
import com.rtz.ordership.entity.OrderItem;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.ShopifyReference;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.PaymentMethod;
import com.rtz.ordership.entity.enums.PaymentStatus;
import com.rtz.ordership.entity.enums.ShippingMethod;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.util.SearchPatterns;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final ProductRepository productRepository;
    // El servidor corre en UTC: los días del filtro se cuentan con la hora del negocio
    @Value("${app.timezone:America/Asuncion}")
    private ZoneId zone = ZoneId.of("America/Asuncion");

    // Transiciones de estado válidas
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED),
            OrderStatus.ASSIGNED, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED),
            OrderStatus.IN_TRANSIT, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    // Estos solo los cambia DeliveryAssignmentService, junto con la entrega
    private static final Set<OrderStatus> DELIVERY_STATUSES =
            Set.of(OrderStatus.ASSIGNED, OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED);

    public OrderService(OrderRepository orderRepository,
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
    }

    // ── Listar pedidos (paginado + filtros opcionales + customerId) ──────────

    private static final LocalDate FIRST_DAY = LocalDate.of(2000, 1, 1);
    private static final LocalDate LAST_DAY = LocalDate.of(9999, 12, 31);
    private static final Instant NO_LIMIT = Instant.parse("9999-12-31T00:00:00Z");

    // Rango de días [from, to]: en la agenda, por fecha de entrega; si no, por fecha de creación en hora del negocio
    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(OrderStatus status, LocalDate deliveryDate,
            UUID customerId, OrderSource source, String query, boolean scheduled, boolean missingDeliveryCost,
            LocalDate from, LocalDate to, Pageable pageable) {
        log.info("Listando pedidos | status: {} | fecha: {} | cliente: {} | source: {} | query: {} | agenda: {} "
                        + "| sin costo de delivery: {} | desde: {} | hasta: {}",
                status, deliveryDate, customerId, source, query, scheduled, missingDeliveryCost, from, to);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha \"desde\" no puede ser posterior a \"hasta\"");
        }

        // Sin rango van los extremos: Postgres no puede tipar un parámetro null en "IS NULL"
        LocalDate deliveryFrom = scheduled && from != null ? from : FIRST_DAY;
        LocalDate deliveryTo = scheduled && to != null ? to : LAST_DAY;
        Instant createdFrom = !scheduled && from != null ? from.atStartOfDay(zone).toInstant() : Instant.EPOCH;
        Instant createdBefore = !scheduled && to != null ? to.plusDays(1).atStartOfDay(zone).toInstant() : NO_LIMIT;

        Page<Order> page = orderRepository.search(customerId, status, deliveryDate,
                deliveryFrom, deliveryTo, createdFrom, createdBefore, source,
                SearchPatterns.containsLike(query), SearchPatterns.phoneContainsLike(query),
                SearchPatterns.orderNumber(query), scheduled, missingDeliveryCost,
                missingDeliveryCostSince(Instant.now()), pageable);

        Page<OrderResponse> result = page.map(OrderResponse::fromEntity);
        log.info("Se encontraron {} pedidos en la página (Total: {})",
                result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    // ── Pedidos por cliente (para historial) ────────────────────────────────

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByCustomerId(UUID customerId, Pageable pageable) {
        log.info("Listando historial de pedidos del cliente ID: {}", customerId);
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Cliente no encontrado con ID: " + customerId);
        }
        Page<OrderResponse> result = orderRepository.findByCustomerId(customerId, pageable)
                .map(OrderResponse::fromEntity);
        log.info("Se encontraron {} pedidos del cliente", result.getTotalElements());
        return result;
    }

    // ── Obtener pedido por ID ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        log.info("Obteniendo detalle del pedido ID: {}", id);
        Order order = findOrderOrThrow(id);
        return OrderResponse.fromEntity(order);
    }

    // ── Crear pedido con ítems + descuento de stock ─────────────────────────

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creando pedido manual para cliente ID: {} - {} ítems", request.customerId(), request.items().size());

        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Cliente no encontrado con ID: " + request.customerId()));

        CustomerAddress address = null;
        if (request.customerAddressId() != null) {
            address = addressRepository.findById(request.customerAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Dirección no encontrada con ID: " + request.customerAddressId()));
            if (!address.getCustomer().getId().equals(customer.getId())) {
                throw new ResourceNotFoundException("La dirección no pertenece al cliente especificado");
            }
        }

        // El cliente ya lo pidió directamente: nace confirmado y con fecha de entrega
        Order order = Order.builder()
                .customer(customer)
                .customerAddress(address)
                .createdBy(currentUser)
                .status(OrderStatus.CONFIRMED)
                .confirmedAt(Instant.now())
                .source(OrderSource.MANUAL)
                .notes(blankToNull(request.notes()))
                .deliveryDate(request.deliveryDate())
                .totalAmount(BigDecimal.ZERO)
                .amountToCollect(BigDecimal.ZERO)
                .build();
        applyPayment(order, request.paymentStatus() != null ? request.paymentStatus() : PaymentStatus.UNPAID,
                request.paymentMethod());

        order.getItems().addAll(buildManualItems(order, request.items(), Map.of()));
        applyTotals(order, request.deliveryFee(), discountLines(request.discounts(), request.discount()));

        Order saved = orderRepository.save(order);
        log.info("Pedido manual creado - id: {}, cliente: {}, a cobrar: {}, ítems: {}",
                saved.getId(), customer.getFullName(), saved.getAmountToCollect(), saved.getItems().size());
        return OrderResponse.fromEntity(saved);
    }

    // ── Editar los productos de un pedido manual ────────────────────────────

    /**
     * Reemplaza los ítems: devuelve el stock de los anteriores y descuenta el de los nuevos. Los productos que ya
     * estaban conservan el precio con el que se vendieron; los nuevos toman el del catálogo.
     */
    @Transactional
    public OrderResponse updateItems(UUID id, OrderItemsUpdateRequest request) {
        log.info("Editando productos del pedido ID: {} - {} ítems", id, request.items().size());
        Order order = findOrderOrThrow(id);

        if (order.getSource() != OrderSource.MANUAL) {
            throw new IllegalStateException(
                    "Los productos de un pedido de Shopify no se editan: el cliente ya pagó ese total en Shopify");
        }
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se pueden editar los productos de un pedido " + order.getStatus());
        }

        Map<UUID, OrderItem> soldItems = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            soldItems.put(item.getProduct().getId(), item);
        }
        restoreStock(order);
        List<OrderItem> items = buildManualItems(order, request.items(), soldItems);
        order.getItems().clear();
        order.getItems().addAll(items);
        applyTotals(order, request.deliveryFee(), discountLines(request.discounts(), request.discount()));

        Order saved = orderRepository.save(order);
        log.info("Pedido ID: {} - productos editados, a cobrar: {}", id, saved.getAmountToCollect());
        return OrderResponse.fromEntity(saved);
    }

    /**
     * Ítems de un pedido manual: un producto repetido se suma en una sola línea, no se venden productos
     * desactivados ni con precio en dólares, y la falta de stock no bloquea (queda negativo, igual que Shopify).
     * Los que ya estaban en el pedido ({@code soldItems}) conservan el precio y el costo con que se vendieron.
     */
    private List<OrderItem> buildManualItems(Order order, List<OrderItemRequest> requests,
            Map<UUID, OrderItem> soldItems) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderItemRequest request : requests) {
            quantities.merge(request.productId(), request.quantity(), Integer::sum);
        }
        return quantities.entrySet().stream().map(entry -> {
            Product product = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + entry.getKey()));
            OrderItem sold = soldItems.get(product.getId());
            if (!product.getActive() && sold == null) {
                throw new IllegalStateException("El producto '" + product.getName() + "' está desactivado");
            }
            if (product.getCurrency() != Currency.PYG) {
                throw new IllegalStateException("'" + product.getName()
                        + "' tiene el precio en dólares; los pedidos se cobran en guaraníes");
            }
            if (sold == null) {
                return buildOrderItem(order, product, entry.getValue(), product.getSalePrice(), costOf(product));
            }
            BigDecimal unitCost = sold.getUnitCost() != null ? sold.getUnitCost() : costOf(product);
            return buildOrderItem(order, product, entry.getValue(), sold.getUnitPrice(), unitCost);
        }).toList();
    }

    // Las apps anteriores mandan un solo descuento sin motivo; si viene la lista, manda la lista
    private static List<OrderDiscountRequest> discountLines(List<OrderDiscountRequest> discounts, BigDecimal discount) {
        if (discounts != null) {
            return discounts;
        }
        return discount != null && discount.signum() > 0 ? List.of(new OrderDiscountRequest(null, discount)) : List.of();
    }

    // A cobrar = productos + envío - descuentos
    private void applyTotals(Order order, BigDecimal deliveryFee, List<OrderDiscountRequest> discounts) {
        BigDecimal fee = deliveryFee != null ? deliveryFee : BigDecimal.ZERO;
        order.getDiscounts().clear();
        for (int i = 0; i < discounts.size(); i++) {
            OrderDiscountRequest discount = discounts.get(i);
            order.getDiscounts().add(OrderDiscount.builder()
                    .order(order)
                    .label(blankToNull(discount.label()))
                    .amount(discount.amount())
                    .position(i)
                    .build());
        }
        BigDecimal off = discounts.stream().map(OrderDiscountRequest::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = sumSubtotals(order.getItems());
        BigDecimal toCollect = total.add(fee).subtract(off);
        if (toCollect.signum() < 0) {
            throw new IllegalArgumentException("El descuento no puede ser mayor que el total del pedido");
        }
        order.setTotalAmount(total);
        order.setDeliveryFee(fee);
        order.setDiscount(off);
        order.setAmountToCollect(toCollect);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ── Crear pedido a partir de un webhook de Shopify ──────────────────────

    /**
     * Si {@code shopify.amountToCollect()} es null (no vino el total de Shopify), se cobra el total calculado
     * con los precios del catálogo.
     */
    @Transactional
    public OrderResponse createOrderFromShopify(Customer customer, ShopifyOrderDetails shopify,
            List<OrderItemRequest> items) {

        String shopifyOrderId = shopify.shopifyOrderId();
        if (orderRepository.existsByShopify_OrderId(shopifyOrderId)) {
            log.info("Pedido de Shopify {} ya fue procesado, se ignora", shopifyOrderId);
            return OrderResponse.fromEntity(orderRepository.findByShopify_OrderId(shopifyOrderId).orElseThrow());
        }

        Order order = Order.builder()
                .customer(customer)
                .customerAddress(null)
                .createdBy(null)
                .status(OrderStatus.PENDING)
                .source(OrderSource.SHOPIFY)
                .shippingMethod(ShippingMethod.OWN_DELIVERY)
                .shopify(ShopifyReference.builder()
                        .orderId(shopifyOrderId)
                        .orderName(shopify.orderName())
                        .adminUrl(shopify.adminUrl())
                        .build())
                .shippingAddressRaw(shopify.shippingAddressRaw())
                .totalAmount(BigDecimal.ZERO)
                .amountToCollect(BigDecimal.ZERO)
                .build();

        // La venta ya ocurrió en Shopify (el cliente pagó): no se rechaza por stock ni por producto desactivado
        List<OrderItem> orderItems = buildShopifyItems(order, items);
        BigDecimal totalAmount = sumSubtotals(orderItems);

        order.setTotalAmount(totalAmount);
        order.setAmountToCollect(shopify.amountToCollect() != null ? shopify.amountToCollect() : totalAmount);
        order.getItems().addAll(orderItems);

        Order saved = orderRepository.save(order);
        log.info("Pedido Shopify creado - id: {}, pedido Shopify: {} ({}), cliente: {}, a cobrar: {}, "
                        + "total catálogo: {}, ítems: {}",
                saved.getId(), shopify.orderName(), shopifyOrderId, customer.getFullName(),
                saved.getAmountToCollect(), totalAmount, orderItems.size());
        return OrderResponse.fromEntity(saved);
    }

    // ── Helpers de ítems ────────────────────────────────────────────────────

    private List<OrderItem> buildShopifyItems(Order order, List<OrderItemRequest> items) {
        return items.stream().map(itemReq -> {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado con ID: " + itemReq.productId()));
            if (!product.getActive()) {
                log.warn("Pedido Shopify con producto desactivado: '{}'", product.getName());
            }
            return buildOrderItem(order, product, itemReq.quantity(), product.getSalePrice(), costOf(product));
        }).toList();
    }

    /**
     * Precio de compra que se congela en el ítem. Null si el producto no lo tiene (creado desde Shopify, por
     * completar) o está en dólares: se completa cuando se carga el precio de compra del producto.
     */
    static BigDecimal costOf(Product product) {
        if (Boolean.TRUE.equals(product.getNeedsReview()) || product.getCurrency() != Currency.PYG
                || product.getPurchasePrice() == null || product.getPurchasePrice().signum() <= 0) {
            return null;
        }
        return product.getPurchasePrice();
    }

    // Descuenta el stock; si no alcanza queda negativo: la venta ya se hizo y no se bloquea
    private OrderItem buildOrderItem(Order order, Product product, Integer quantity, BigDecimal unitPrice,
            BigDecimal unitCost) {
        if (product.getStock() < quantity) {
            log.warn("Pedido deja stock negativo para '{}': disponible {}, vendido {}",
                    product.getName(), product.getStock(), quantity);
        }

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);

        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        return OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .unitCost(unitCost)
                .build();
    }

    private BigDecimal sumSubtotals(List<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            total = total.add(item.getSubtotal());
        }
        return total;
    }

    // ── Cambiar estado del pedido ───────────────────────────────────────────

    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatusUpdateRequest request) {
        log.info("Cambiando estado del pedido ID: {} → {}", id, request.status());

        Order order = findOrderOrThrow(id);
        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.status();

        if (DELIVERY_STATUSES.contains(newStatus)) {
            throw new IllegalStateException(
                    "Los estados de reparto se cambian desde la entrega del pedido (asignar repartidor, en camino, entregado)");
        }

        // Validar transición
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    String.format("Transición inválida: %s → %s. Transiciones permitidas: %s",
                            currentStatus, newStatus, allowed));
        }

        if (newStatus == OrderStatus.CONFIRMED) {
            order.setConfirmedAt(Instant.now());
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
            closeActiveDeliveries(order);
        }

        order.setStatus(newStatus);
        order = orderRepository.save(order);
        log.info("Pedido ID: {} cambió de {} → {}", id, currentStatus, newStatus);
        return OrderResponse.fromEntity(order);
    }

    // ── Actualizar estado de pago ───────────────────────────────────────────

    @Transactional
    public OrderResponse updatePaymentStatus(UUID id, PaymentStatusUpdateRequest request) {
        log.info("Actualizando pago del pedido ID: {} → {} ({})", id, request.paymentStatus(), request.paymentMethod());
        Order order = findOrderOrThrow(id);
        applyPayment(order, request.paymentStatus(), request.paymentMethod());
        order = orderRepository.save(order);
        log.info("Pedido ID: {} - pago actualizado a {} ({})", id, order.getPaymentStatus(), order.getPaymentMethod());
        return OrderResponse.fromEntity(order);
    }

    // Sin pagar no tiene forma de pago; pagado o parcial sin forma deja la que ya tenía (apps sin el selector)
    private void applyPayment(Order order, PaymentStatus status, PaymentMethod method) {
        order.setPaymentStatus(status);
        if (status == PaymentStatus.UNPAID) {
            order.setPaymentMethod(null);
        } else if (method != null) {
            order.setPaymentMethod(method);
        }
    }

    // ── Completar dirección de un pedido (pedidos Shopify sin dirección) ────

    @Transactional
    public OrderResponse updateOrderAddress(UUID id, OrderAddressUpdateRequest request) {
        log.info("Asignando dirección {} al pedido ID: {}", request.customerAddressId(), id);
        Order order = findOrderOrThrow(id);

        CustomerAddress address = addressRepository.findById(request.customerAddressId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dirección no encontrada con ID: " + request.customerAddressId()));

        if (!address.getCustomer().getId().equals(order.getCustomer().getId())) {
            throw new ResourceNotFoundException("La dirección no pertenece al cliente del pedido");
        }

        order.setCustomerAddress(address);
        order = orderRepository.save(order);
        log.info("Pedido ID: {} - dirección asignada: {}", id, address.getId());
        return OrderResponse.fromEntity(order);
    }

    // ── Fecha de entrega (agenda) ───────────────────────────────────────────

    @Transactional
    public OrderResponse updateDeliveryDate(UUID id, LocalDate deliveryDate) {
        log.info("Programando entrega del pedido ID: {} para {}", id, deliveryDate);
        Order order = findOrderOrThrow(id);
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new IllegalStateException("Confirmá el pedido antes de programar la entrega");
        }
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede programar la entrega de un pedido " + order.getStatus());
        }
        order.setDeliveryDate(deliveryDate);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    // ── Costo del delivery (ganancia) ────────────────────────────────────────

    // El aviso de entregados sin costo mira solo los pedidos recientes: los viejos no se van a completar
    static final int MISSING_DELIVERY_COST_DAYS = 30;

    static Instant missingDeliveryCostSince(Instant now) {
        return now.minus(Duration.ofDays(MISSING_DELIVERY_COST_DAYS));
    }

    @Transactional
    public OrderResponse updateDeliveryCost(UUID id, BigDecimal deliveryCost) {
        log.info("Costo del delivery del pedido ID: {} → {}", id, deliveryCost);
        if (deliveryCost != null && deliveryCost.signum() < 0) {
            throw new IllegalArgumentException("El costo del delivery no puede ser negativo");
        }
        Order order = findOrderOrThrow(id);
        order.setDeliveryCost(deliveryCost);
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    // Nota interna del operador: no va al cliente ni al repartidor. Vacía la borra
    @Transactional
    public OrderResponse updateNotes(UUID id, String notes) {
        log.info("Nota del pedido ID: {}", id);
        Order order = findOrderOrThrow(id);
        order.setNotes(blankToNull(notes));
        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public AgendaSummaryResponse getAgendaSummary(LocalDate today, OrderSource source) {
        return new AgendaSummaryResponse(
                orderRepository.countScheduledBefore(today, source),
                orderRepository.countScheduledOn(today, source));
    }

    // ── Cancelar pedido + devolver stock ─────────────────────────────────────

    @Transactional
    public void cancelOrder(UUID id) {
        log.info("Cancelando pedido ID: {}", id);
        Order order = findOrderOrThrow(id);

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cancelar un pedido en estado: " + order.getStatus());
        }

        restoreStock(order);
        closeActiveDeliveries(order);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Pedido cancelado - id: {}", id);
    }

    private void closeActiveDeliveries(Order order) {
        for (DeliveryAssignment delivery : order.getDeliveryAssignments()) {
            if (delivery.isActive()) {
                delivery.setStatus(DeliveryStatus.FAILED);
                delivery.setFailureReason("Pedido cancelado");
                delivery.setCompletedAt(Instant.now());
                log.info("Entrega {} cerrada por cancelación del pedido {}", delivery.getId(), order.getId());
            }
        }
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
            log.info("Stock devuelto: {} +{} unidades", product.getName(), item.getQuantity());
        }
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    protected Order findOrderOrThrow(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Pedido no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Pedido no encontrado con ID: " + id);
                });
    }
}
