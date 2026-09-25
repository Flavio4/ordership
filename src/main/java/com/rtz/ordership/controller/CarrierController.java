package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.CarrierRequest;
import com.rtz.ordership.dto.response.CarrierResponse;
import com.rtz.ordership.service.CarrierService;
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
@RequestMapping("/api/carriers")
@Tag(name = "Repartidores", description = "Repartidores propios y couriers tercerizados")
public class CarrierController {

    private final CarrierService carrierService;

    public CarrierController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Listar repartidores", description = "Propios primero y después couriers, por nombre. "
            + "Por defecto solo los activos; includeInactive=true para ver todos")
    public ResponseEntity<List<CarrierResponse>> getCarriers(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(carrierService.getCarriers(includeInactive));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Crear repartidor", description = "type: OWN (propio) o COURIER. userId opcional: usuario DELIVERY de un repartidor propio")
    public ResponseEntity<CarrierResponse> createCarrier(@Valid @RequestBody CarrierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carrierService.createCarrier(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Actualizar repartidor")
    public ResponseEntity<CarrierResponse> updateCarrier(@PathVariable UUID id, @Valid @RequestBody CarrierRequest request) {
        return ResponseEntity.ok(carrierService.updateCarrier(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Desactivar repartidor", description = "No se borra: sus entregas pasadas siguen mostrándolo")
    public ResponseEntity<Void> deactivateCarrier(@PathVariable UUID id) {
        carrierService.deactivateCarrier(id);
        return ResponseEntity.noContent().build();
    }
}
