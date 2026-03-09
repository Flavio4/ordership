package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.DeliveryAssignmentRequest;
import com.rtz.ordership.dto.request.DeliveryStatusUpdateRequest;
import com.rtz.ordership.dto.response.DeliveryAssignmentResponse;
import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.Role;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.DeliveryAssignmentRepository;
import com.rtz.ordership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository deliveryRepository;
    private final OrderService orderService;
    private final UserRepository userRepository;

    // Transiciones de estado válidas para la entrega
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> VALID_TRANSITIONS = Map.of(
            DeliveryStatus.ASSIGNED, Set.of(DeliveryStatus.IN_TRANSIT),
            DeliveryStatus.IN_TRANSIT, Set.of(DeliveryStatus.DELIVERED, DeliveryStatus.FAILED),
            DeliveryStatus.DELIVERED, Set.of(),
            DeliveryStatus.FAILED, Set.of());

    public DeliveryAssignmentService(DeliveryAssignmentRepository deliveryRepository,
            OrderService orderService,
            UserRepository userRepository) {
        this.deliveryRepository = deliveryRepository;
        this.orderService = orderService;
        this.userRepository = userRepository;
    }

    // ── Listar asignaciones (paginado + filtros) ────────────────────────────

    @Transactional(readOnly = true)
    public Page<DeliveryAssignmentResponse> getAllAssignments(
            DeliveryStatus status, UUID zoneId, UUID deliveryUserId, Pageable pageable) {

        log.info("Listando asignaciones | status: {} | zona: {} | repartidor: {}", status, zoneId, deliveryUserId);

        Page<DeliveryAssignment> page;

        if (status != null && deliveryUserId != null && zoneId != null) {
            page = deliveryRepository.findByStatusAndDeliveryUserIdAndZoneId(status, deliveryUserId, zoneId, pageable);
        } else if (status != null && zoneId != null) {
            page = deliveryRepository.findByStatusAndZoneId(status, zoneId, pageable);
        } else if (status != null && deliveryUserId != null) {
            page = deliveryRepository.findByStatusAndDeliveryUserId(status, deliveryUserId, pageable);
        } else if (deliveryUserId != null && zoneId != null) {
            page = deliveryRepository.findByDeliveryUserIdAndZoneId(deliveryUserId, zoneId, pageable);
        } else if (status != null) {
            page = deliveryRepository.findByStatus(status, pageable);
        } else if (zoneId != null) {
            page = deliveryRepository.findByZoneId(zoneId, pageable);
        } else if (deliveryUserId != null) {
            page = deliveryRepository.findByDeliveryUserId(deliveryUserId, pageable);
        } else {
            page = deliveryRepository.findAll(pageable);
        }

        Page<DeliveryAssignmentResponse> result = page.map(DeliveryAssignmentResponse::fromEntity);
        log.info("Se encontraron {} asignaciones en la página (Total: {})",
                result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    // ── Obtener asignación por ID ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public DeliveryAssignmentResponse getAssignmentById(UUID id) {
        log.info("Obteniendo detalle de asignación ID: {}", id);
        DeliveryAssignment assignment = findAssignmentOrThrow(id);
        return DeliveryAssignmentResponse.fromEntity(assignment);
    }

    // ── Asignar pedido a repartidor ─────────────────────────────────────────

    @Transactional
    public DeliveryAssignmentResponse assignOrder(DeliveryAssignmentRequest request) {
        log.info("Asignando pedido ID: {} al repartidor ID: {}", request.orderId(), request.deliveryUserId());

        // 1. Validar que el pedido exista y esté en PENDING
        Order order = orderService.findOrderOrThrow(request.orderId());
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Solo se pueden asignar pedidos en estado PENDING. Estado actual: " + order.getStatus());
        }

        // 2. Validar que no esté ya asignado
        if (deliveryRepository.existsByOrderId(request.orderId())) {
            throw new IllegalStateException("Este pedido ya tiene una asignación de entrega");
        }

        // 3. Validar que el usuario sea DELIVERY y esté activo
        User deliveryUser = userRepository.findById(request.deliveryUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Repartidor no encontrado con ID: " + request.deliveryUserId()));

        if (deliveryUser.getRole() != Role.DELIVERY) {
            throw new IllegalStateException(
                    "El usuario '" + deliveryUser.getFullName() + "' no tiene el rol DELIVERY");
        }
        if (!deliveryUser.getActive()) {
            throw new IllegalStateException(
                    "El repartidor '" + deliveryUser.getFullName() + "' está desactivado");
        }

        // 4. Crear la asignación (zona se copia de la dirección del pedido)
        DeliveryAssignment assignment = DeliveryAssignment.builder()
                .order(order)
                .deliveryUser(deliveryUser)
                .zone(order.getCustomerAddress().getZone())
                .status(DeliveryStatus.ASSIGNED)
                .notes(request.notes())
                .build();

        assignment = deliveryRepository.save(assignment);

        // 5. Sincronizar: el pedido pasa a ASSIGNED
        order.setStatus(OrderStatus.ASSIGNED);
        log.info("Asignación creada - id: {} | Pedido {} → ASSIGNED | Repartidor: {}",
                assignment.getId(), order.getId(), deliveryUser.getFullName());

        return DeliveryAssignmentResponse.fromEntity(assignment);
    }

    // ── Actualizar estado de entrega ────────────────────────────────────────

    @Transactional
    public DeliveryAssignmentResponse updateStatus(UUID id, DeliveryStatusUpdateRequest request) {
        log.info("Actualizando estado de entrega ID: {} → {}", id, request.status());

        DeliveryAssignment assignment = findAssignmentOrThrow(id);
        DeliveryStatus currentStatus = assignment.getStatus();
        DeliveryStatus newStatus = request.status();

        // Validar transición
        Set<DeliveryStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    String.format("Transición inválida: %s → %s. Transiciones permitidas: %s",
                            currentStatus, newStatus, allowed));
        }

        assignment.setStatus(newStatus);

        // Actualizar notas si se proporcionaron
        if (request.notes() != null) {
            assignment.setNotes(request.notes());
        }

        // Sincronizar con el estado del pedido
        Order order = assignment.getOrder();
        switch (newStatus) {
            case IN_TRANSIT -> {
                order.setStatus(OrderStatus.IN_TRANSIT);
                log.info("Pedido {} → IN_TRANSIT", order.getId());
            }
            case DELIVERED -> {
                order.setStatus(OrderStatus.DELIVERED);
                assignment.setCompletedAt(Instant.now());
                log.info("Pedido {} → DELIVERED | Entrega completada", order.getId());
            }
            case FAILED -> {
                order.setStatus(OrderStatus.PENDING);
                assignment.setCompletedAt(Instant.now());
                log.info("Pedido {} → PENDING (entrega fallida, disponible para reasignar)", order.getId());
            }
            default -> {
                /* ASSIGNED ya fue manejado */ }
        }

        assignment = deliveryRepository.save(assignment);
        log.info("Entrega ID: {} cambió de {} → {}", id, currentStatus, newStatus);
        return DeliveryAssignmentResponse.fromEntity(assignment);
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private DeliveryAssignment findAssignmentOrThrow(UUID id) {
        return deliveryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Asignación no encontrada con ID: {}", id);
                    return new ResourceNotFoundException("Asignación no encontrada con ID: " + id);
                });
    }
}
