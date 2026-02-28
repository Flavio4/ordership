package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.ZoneRequest;
import com.rtz.ordership.dto.request.ZoneUpdateRequest;
import com.rtz.ordership.dto.response.ZoneResponse;
import com.rtz.ordership.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/zones")
@Tag(name = "Zonas", description = "Gestión de zonas de reparto")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Listar zonas", description = "Obtiene todas las zonas activas")
    public ResponseEntity<List<ZoneResponse>> getAllZones() {
        return ResponseEntity.ok(zoneService.getAllZones());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Obtener zona", description = "Obtiene una zona por ID")
    public ResponseEntity<ZoneResponse> getZoneById(@PathVariable UUID id) {
        return ResponseEntity.ok(zoneService.getZoneById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Crear zona", description = "Crea una nueva zona de reparto")
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody ZoneRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.createZone(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Actualizar zona", description = "Actualiza una zona existente")
    public ResponseEntity<ZoneResponse> updateZone(@PathVariable UUID id,
            @RequestBody ZoneUpdateRequest request) {
        return ResponseEntity.ok(zoneService.updateZone(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Desactivar zona", description = "Desactiva una zona (soft delete)")
    public ResponseEntity<Void> deactivateZone(@PathVariable UUID id) {
        zoneService.deactivateZone(id);
        return ResponseEntity.noContent().build();
    }
}
