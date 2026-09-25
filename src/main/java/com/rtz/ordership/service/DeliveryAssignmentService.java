package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.DeliveryAssignmentRequest;
import com.rtz.ordership.dto.request.DeliveryStatusUpdateRequest;
import com.rtz.ordership.dto.response.DeliveryAssignmentResponse;
import com.rtz.ordership.entity.Carrier;
import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.CarrierType;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.ShippingMethod;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.CarrierRepository;
import com.rtz.ordership.repository.DeliveryAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class DeliveryAssignmentService {

    static final Set<DeliveryStatus> ACTIVE_STATUSES = Set.of(DeliveryStatus.ASSIGNED, DeliveryStatus.IN_TRANSIT);

    // "En camino" es opcional: quien marca en papel suele pasar directo a entregado
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> VALID_TRANSITIONS = Map.of(
            DeliveryStatus.ASSIGNED, Set.of(DeliveryStatus.IN_TRANSIT, DeliveryStatus.DELIVERED, DeliveryStatus.FAILED),
            DeliveryStatus.IN_TRANSIT, Set.of(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED),
            DeliveryStatus.DELIVERED, Set.of(),
            DeliveryStatus.FAILED, Set.of());

    private final DeliveryAssignmentRepository deliveryRepository;
    private final OrderService orderService;
    private final CarrierService carrierService;
    private final CarrierRepository carrierRepository;

    public DeliveryAssignmentService(DeliveryAssignmentRepository deliveryRepository,
            OrderService orderService,
            CarrierService carrierService,
            CarrierRepository carrierRepository) {
        this.deliveryRepository = deliveryRepository;
        this.orderService = orderService;
        this.carrierService = carrierService;
        this.carrierRepository = carrierRepository;
    }

    @Transactional(readOnly = true)
    public Page<DeliveryAssignmentResponse> getAllAssignments(
            DeliveryStatus status, UUID zoneId, UUID carrierId, Pageable pageable) {
        log.info("Listando entregas | status: {} | zona: {} | repartidor: {}", status, zoneId, carrierId);
        return deliveryRepository.search(status, zoneId, carrierId, pageable)
                .map(DeliveryAssignmentResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public DeliveryAssignmentResponse getAssignmentById(UUID id) {
        return DeliveryAssignmentResponse.fromEntity(findAssignmentOrThrow(id));
    }

    // ── Asignar el reparto ──────────────────────────────────────────────────

    @Transactional
    public DeliveryAssignmentResponse assignOrder(DeliveryAssignmentRequest request) {
        return DeliveryAssignmentResponse.fromEntity(assign(request));
    }

    @Transactional
    public DeliveryAssignment assign(DeliveryAssignmentRequest request) {
        log.info("Asignando pedido ID: {} al repartidor ID: {}", request.orderId(), request.carrierId());

        Order order = orderService.findOrderOrThrow(request.orderId());
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Solo se puede asignar el reparto de pedidos confirmados. Estado actual: " + order.getStatus());
        }

        Carrier carrier = carrierService.findCarrierOrThrow(request.carrierId());
        if (!carrier.getActive()) {
            throw new IllegalStateException("El repartidor '" + carrier.getName() + "' está desactivado");
        }

        boolean courier = carrier.getType() == CarrierType.COURIER;
        if (!courier && order.getCustomerAddress() == null) {
            throw new IllegalStateException(
                    "Para un repartidor propio el pedido necesita una dirección con zona. "
                            + "Asignale una dirección o despachalo por courier.");
        }

        if (deliveryRepository.existsByOrderIdAndStatusIn(order.getId(), ACTIVE_STATUSES)) {
            throw new IllegalStateException("Este pedido ya tiene una entrega en curso");
        }

        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .order(order)
                .carrier(carrier)
                .zone(order.getCustomerAddress() != null ? order.getCustomerAddress().getZone() : null)
                .status(DeliveryStatus.ASSIGNED)
                .notes(blankToNull(request.notes()))
                .build();
        assignment = deliveryRepository.save(assignment);
        order.getDeliveryAssignments().addFirst(assignment);

        order.setShippingMethod(courier ? ShippingMethod.COURIER : ShippingMethod.OWN_DELIVERY);
        order.setCourierName(courier ? carrier.getName() : null);
        order.setTrackingCode(courier ? blankToNull(request.trackingCode()) : null);
        order.setStatus(OrderStatus.ASSIGNED);

        log.info("Entrega creada - id: {} | Pedido {} → ASSIGNED | Repartidor: {} ({})",
                assignment.getId(), order.getId(), carrier.getName(), carrier.getType());
        return assignment;
    }

    // ── Avanzar la entrega ──────────────────────────────────────────────────

    @Transactional
    public DeliveryAssignmentResponse updateStatus(UUID id, DeliveryStatusUpdateRequest request) {
        return DeliveryAssignmentResponse.fromEntity(changeStatus(findAssignmentOrThrow(id), request));
    }

    /** Cambia el estado de la entrega en curso de un pedido. */
    @Transactional
    public void updateActiveDeliveryOfOrder(UUID orderId, DeliveryStatusUpdateRequest request) {
        List<DeliveryAssignment> active = deliveryRepository.findByOrderIdAndStatusIn(orderId, ACTIVE_STATUSES);
        if (active.isEmpty()) {
            throw new IllegalStateException("Este pedido no tiene una entrega en curso");
        }
        changeStatus(active.getFirst(), request);
    }

    private DeliveryAssignment changeStatus(DeliveryAssignment assignment, DeliveryStatusUpdateRequest request) {
        DeliveryStatus currentStatus = assignment.getStatus();
        DeliveryStatus newStatus = request.status();
        log.info("Entrega ID: {} {} → {}", assignment.getId(), currentStatus, newStatus);

        Set<DeliveryStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    String.format("Transición inválida: %s → %s. Transiciones permitidas: %s",
                            currentStatus, newStatus, allowed));
        }

        String note = blankToNull(request.notes());
        if (newStatus == DeliveryStatus.FAILED && note == null) {
            throw new IllegalArgumentException("Contá por qué no se pudo entregar");
        }

        assignment.setStatus(newStatus);
        Order order = assignment.getOrder();
        switch (newStatus) {
            case IN_TRANSIT -> order.setStatus(OrderStatus.IN_TRANSIT);
            case DELIVERED -> {
                order.setStatus(OrderStatus.DELIVERED);
                assignment.setCompletedAt(Instant.now());
                if (note != null) {
                    assignment.setNotes(note);
                }
            }
            case FAILED -> {
                // El cliente ya lo había confirmado: queda listo para otro intento
                order.setStatus(OrderStatus.CONFIRMED);
                assignment.setFailureReason(note);
                assignment.setCompletedAt(Instant.now());
            }
            default -> {
            }
        }
        return deliveryRepository.save(assignment);
    }

    // ── Mis entregas (repartidor con usuario en la app) ─────────────────────

    @Transactional(readOnly = true)
    public Page<DeliveryAssignmentResponse> getMyAssignments(DeliveryStatus status, Pageable pageable) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Carrier carrier = carrierRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Tu usuario no está vinculado a ningún repartidor"));
        return deliveryRepository.search(status, null, carrier.getId(), pageable)
                .map(DeliveryAssignmentResponse::fromEntity);
    }

    private DeliveryAssignment findAssignmentOrThrow(UUID id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada con ID: " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
