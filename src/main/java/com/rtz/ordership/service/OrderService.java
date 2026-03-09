package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.OrderRequest;
import com.rtz.ordership.dto.request.OrderStatusUpdateRequest;
import com.rtz.ordership.dto.response.OrderResponse;
import com.rtz.ordership.entity.*;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final ProductRepository productRepository;

    // Transiciones de estado válidas
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.ASSIGNED, OrderStatus.CANCELLED),
            OrderStatus.ASSIGNED, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED),
            OrderStatus.IN_TRANSIT, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.DELIVERED, Set.of(),
            OrderStatus.CANCELLED, Set.of());

    public OrderService(OrderRepository orderRepository,
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
    }

    // ── Listar pedidos (paginado + filtros opcionales) ──────────────────────

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(OrderStatus status, LocalDate deliveryDate, Pageable pageable) {
        log.info("Listando pedidos | status: {} | fecha: {}", status, deliveryDate);

        Page<Order> page;
        if (status != null && deliveryDate != null) {
            page = orderRepository.findByStatusAndDeliveryDate(status, deliveryDate, pageable);
        } else if (status != null) {
            page = orderRepository.findByStatus(status, pageable);
        } else if (deliveryDate != null) {
            page = orderRepository.findByDeliveryDate(deliveryDate, pageable);
        } else {
            page = orderRepository.findAll(pageable);
        }

        Page<OrderResponse> result = page.map(OrderResponse::fromEntity);
        log.info("Se encontraron {} pedidos en la página (Total: {})",
                result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    // ── Obtener pedido por ID ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id) {
        log.info("Obteniendo detalle del pedido ID: {}", id);
        Order order = findOrderOrThrow(id);
        return OrderResponse.fromEntity(order);
    }

    // ── Crear pedido con ítems ──────────────────────────────────────────────

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

        // Construir el pedido
        Order order = Order.builder()
                .customer(customer)
                .customerAddress(address)
                .createdBy(currentUser)
                .status(OrderStatus.PENDING)
                .notes(request.notes())
                .deliveryDate(request.deliveryDate())
                .totalAmount(BigDecimal.ZERO) // se calcula abajo
                .build();

        // Construir ítems y calcular totales
        BigDecimal totalAmount = BigDecimal.ZERO;

        List<OrderItem> orderItems = request.items().stream().map(itemReq -> {
            Product product = productRepository.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Producto no encontrado con ID: " + itemReq.productId()));

            if (!product.getActive()) {
                throw new IllegalStateException("El producto '" + product.getName() + "' está desactivado");
            }

            BigDecimal unitPrice = product.getSalePrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));

            return OrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(itemReq.quantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();
        }).toList();

        // Calcular el total
        for (OrderItem item : orderItems) {
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        order.setTotalAmount(totalAmount);
        order.getItems().addAll(orderItems);

        Order saved = orderRepository.save(order);
        log.info("Pedido creado - id: {}, cliente: {}, total: {}, ítems: {}",
                saved.getId(), customer.getFullName(), totalAmount, orderItems.size());
        return OrderResponse.fromEntity(saved);
    }

    // ── Cambiar estado del pedido ───────────────────────────────────────────

    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatusUpdateRequest request) {
        log.info("Cambiando estado del pedido ID: {} → {}", id, request.status());

        Order order = findOrderOrThrow(id);
        OrderStatus currentStatus = order.getStatus();
        OrderStatus newStatus = request.status();

        // Validar transición
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    String.format("Transición inválida: %s → %s. Transiciones permitidas: %s",
                            currentStatus, newStatus, allowed));
        }

        order.setStatus(newStatus);
        order = orderRepository.save(order);
        log.info("Pedido ID: {} cambió de {} → {}", id, currentStatus, newStatus);
        return OrderResponse.fromEntity(order);
    }

    // ── Cancelar pedido ─────────────────────────────────────────────────────

    @Transactional
    public void cancelOrder(UUID id) {
        log.info("Cancelando pedido ID: {}", id);
        Order order = findOrderOrThrow(id);

        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("No se puede cancelar un pedido en estado: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Pedido cancelado - id: {}", id);
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
