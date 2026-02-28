package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.CustomerAddressRequest;
import com.rtz.ordership.dto.request.CustomerAddressUpdateRequest;
import com.rtz.ordership.dto.response.CustomerAddressResponse;
import com.rtz.ordership.service.CustomerAddressService;
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
@RequestMapping("/api/customers/{customerId}/addresses")
@Tag(name = "Direcciones de Clientes", description = "Gestión de ubicaciones por cliente")
public class CustomerAddressController {

    private final CustomerAddressService addressService;

    public CustomerAddressController(CustomerAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Listar direcciones", description = "Obtiene todas las direcciones de un cliente")
    public ResponseEntity<List<CustomerAddressResponse>> getCustomerAddresses(@PathVariable UUID customerId) {
        return ResponseEntity.ok(addressService.getCustomerAddresses(customerId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Agregar dirección", description = "Agrega una nueva dirección a un cliente")
    public ResponseEntity<CustomerAddressResponse> addAddress(@PathVariable UUID customerId,
            @Valid @RequestBody CustomerAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.addAddress(customerId, request));
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Actualizar dirección", description = "Actualiza una dirección de cliente")
    public ResponseEntity<CustomerAddressResponse> updateAddress(@PathVariable UUID customerId,
            @PathVariable UUID addressId,
            @RequestBody CustomerAddressUpdateRequest request) {
        return ResponseEntity.ok(addressService.updateAddress(customerId, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Borrar dirección", description = "Desactiva una dirección de cliente")
    public ResponseEntity<Void> deactivateAddress(@PathVariable UUID customerId,
            @PathVariable UUID addressId) {
        addressService.deactivateAddress(customerId, addressId);
        return ResponseEntity.noContent().build();
    }
}
