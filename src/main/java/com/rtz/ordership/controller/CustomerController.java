package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.CustomerRequest;
import com.rtz.ordership.dto.request.CustomerUpdateRequest;
import com.rtz.ordership.dto.request.CustomerWithAddressRequest;
import com.rtz.ordership.dto.response.CustomerDetailResponse;
import com.rtz.ordership.dto.response.CustomerResponse;
import com.rtz.ordership.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Clientes", description = "Gestión de clientes de la aplicación")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Listar clientes", description = "Obtiene todos los clientes activos filtrados por paginación y nombre (opcional)")
    public ResponseEntity<Page<CustomerDetailResponse>> getAllCustomers(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(customerService.getAllCustomers(name, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Obtener detalle del cliente", description = "Obtiene un cliente por ID incluyendo todas sus direcciones")
    public ResponseEntity<CustomerDetailResponse> getCustomerById(@PathVariable UUID id) {
        return ResponseEntity.ok(customerService.getCustomerDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Crear cliente", description = "Crea un nuevo cliente (requiere ADMIN o OPERATOR)")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.createCustomer(request));
    }

    @PostMapping("/with-address")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Crear cliente con dirección", description = "Crea un cliente y su primera dirección en una sola transacción")
    public ResponseEntity<CustomerResponse> createCustomerWithAddress(
            @Valid @RequestBody CustomerWithAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerService.createCustomerWithAddress(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Actualizar cliente", description = "Actualiza un cliente existente")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable UUID id,
            @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok(customerService.updateCustomer(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Desactivar cliente", description = "Desactiva un cliente (soft delete)")
    public ResponseEntity<Void> deactivateCustomer(@PathVariable UUID id) {
        customerService.deactivateCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
