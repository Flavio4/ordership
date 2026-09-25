package com.rtz.ordership.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rtz.ordership.dto.request.OrderAddressUpdateRequest;
import com.rtz.ordership.dto.request.OrderItemRequest;
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
import com.rtz.ordership.entity.OrderItem;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.ShopifyReference;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
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

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(OrderStatus status, LocalDate deliveryDate,
            UUID customerId, OrderSource source, String query, boolean scheduled, Pageable pageable) {
        log.info("Listando pedidos | status: {} | fecha: {} | cliente: {} | source: {} | query: {} | agenda: {}",
                status, deliveryDate, customerId, source, query, scheduled);

        Page<Order> page = orderRepository.search(customerId, status, deliveryDate, source,
                SearchPatterns.containsLike(query), SearchPatterns.phoneContainsLike(query),
                SearchPatterns.orderNumber(query), scheduled, pageable);

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
        log.info("Creando pedido para cliente ID: {} - {} ítems", request.customerId(), request.items().size());

        // Obtener usuario autenticado
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Validar cliente
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Cliente no encontrado con ID: " + request.customerId()));

        // Validar dirección
        CustomerAddress address = addressRepository.findById(request.customerAddressId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dirección no encontrada con ID: " + request.customerAddressId()));

        if (!address.getCustomer().getId().equals(customer.getId())) {
            throw new ResourceNotFoundException("La dirección no pertenece al cliente especificado");
        }

        ShippingMethod shippingMethod = request.shippingMethod() != null
                ? request.shippingMethod()
                : ShippingMethod.OWN_DELIVERY;
        if (shippingMethod == ShippingMethod.COURIER
                && (request.courierName() == null || request.courierName().isBlank())) {
            throw new IllegalArgumentException("El nombre de la empresa de envíos es obligatorio para envío por courier");
        }

        // Construir el pedido
        Order order = Order.builder()
                .customer(customer)
                .customerAddress(address)
                .createdBy(currentUser)
                .status(OrderStatus.PENDING)
                .source(OrderSource.MANUAL)
                .shippingMethod(shippingMethod)
                .courierName(shippingMethod == ShippingMethod.COURIER ? request.courierName() : null)
                .trackingCode(request.trackingCode())
                .notes(request.notes())
                .deliveryDate(request.deliveryDate())
                .totalAmount(BigDecimal.ZERO) // se calcula abajo
                .amountToCollect(BigDecimal.ZERO)
                .build();

        List<OrderItem> orderItems = buildOrderItems(order, request.items(), true);
        BigDecimal totalAmount = sumSubtotals(orderItems);

        order.setTotalAmount(totalAmount);
        order.setAmountToCollect(totalAmount);
        order.getItems().addAll(orderItems);

        Order saved = orderRepository.save(order);
        log.info("Pedido creado - id: {}, cliente: {}, total: {}, ítems: {}",
                saved.getId(), customer.getFullName(), totalAmount, orderItems.size());
        return OrderResponse.fromEntity(saved);
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
        List<OrderItem> orderItems = buildOrderItems(order, items, false);
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

    // ── Helpers de ítems (compartidos entre creación manual y Shopify) ──────

    /**
     * @param validateAvailability true = rechaza productos desactivados o sin stock suficiente (pedidos manuales).
     *                             false = los acepta y el stock puede quedar negativo (pedidos ya vendidos en Shopify).
     */
    private List<OrderItem> buildOrderItems(Order order, List<OrderItemRequest> items, boolean validateAvailability) {
        return items.stream().map(itemReq -> {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado con ID: " + itemReq.productId()));
            return buildOrderItem(order, product, itemReq.quantity(), validateAvailability);
        }).toList();
    }

    private OrderItem buildOrderItem(Order order, Product product, Integer quantity, boolean validateAvailability) {
        if (!product.getActive()) {
            if (validateAvailability) {
                throw new IllegalStateException("El producto '" + product.getName() + "' está desactivado");
            }
            log.warn("Pedido Shopify con producto desactivado: '{}'", product.getName());
        }

        if (product.getStock() < quantity) {
            if (validateAvailability) {
                throw new IllegalStateException(
                        "Stock insuficiente para '" + product.getName()
                                + "'. Disponible: " + product.getStock()
                                + ", solicitado: " + quantity);
            }
            log.warn("Pedido Shopify deja stock negativo para '{}': disponible {}, vendido {}",
                    product.getName(), product.getStock(), quantity);
        }

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);

        BigDecimal unitPrice = product.getSalePrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

        return OrderItem.builder()
                .order(order)
                .product(product)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
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
        log.info("Actualizando pago del pedido ID: {} → {}", id, request.paymentStatus());
        Order order = findOrderOrThrow(id);
        order.setPaymentStatus(request.paymentStatus());
        order = orderRepository.save(order);
        log.info("Pedido ID: {} - pago actualizado a {}", id, request.paymentStatus());
        return OrderResponse.fromEntity(order);
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
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede programar la entrega de un pedido " + order.getStatus());
        }
        order.setDeliveryDate(deliveryDate);
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
