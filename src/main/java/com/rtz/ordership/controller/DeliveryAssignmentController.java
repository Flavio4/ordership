package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.DeliveryAssignmentRequest;
import com.rtz.ordership.dto.request.DeliveryStatusUpdateRequest;
import com.rtz.ordership.dto.response.DeliveryAssignmentResponse;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import com.rtz.ordership.service.DeliveryAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
@Tag(name = "Entregas", description = "Asignación y seguimiento de entregas a repartidores")
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService deliveryService;

    public DeliveryAssignmentController(DeliveryAssignmentService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Listar entregas", description = "Lista asignaciones de entrega con filtros opcionales por estado, zona y repartidor")
    public ResponseEntity<Page<DeliveryAssignmentResponse>> getAllAssignments(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID deliveryUserId,
            @PageableDefault(size = 20, sort = "assignedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(deliveryService.getAllAssignments(status, zoneId, deliveryUserId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Obtener entrega", description = "Obtiene el detalle de una asignación de entrega")
    public ResponseEntity<DeliveryAssignmentResponse> getAssignmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(deliveryService.getAssignmentById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Asignar pedido", description = "Asigna un pedido PENDING a un repartidor. El pedido pasa automáticamente a ASSIGNED")
    public ResponseEntity<DeliveryAssignmentResponse> assignOrder(
            @Valid @RequestBody DeliveryAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.assignOrder(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Actualizar estado de entrega", description = "Cambia el estado de la entrega y sincroniza automáticamente con el estado del pedido")
    public ResponseEntity<DeliveryAssignmentResponse> updateStatus(@PathVariable UUID id,
            @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        return ResponseEntity.ok(deliveryService.updateStatus(id, request));
    }
}
