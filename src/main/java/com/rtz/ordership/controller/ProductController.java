package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.ProductRequest;
import com.rtz.ordership.dto.request.ProductUpdateRequest;
import com.rtz.ordership.dto.request.StockAdjustmentRequest;
import com.rtz.ordership.dto.response.ProductResponse;
import com.rtz.ordership.service.ProductService;
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
@RequestMapping("/api/products")
@Tag(name = "Productos", description = "Gestión de productos")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Listar productos", description = "Lista productos paginados. Por defecto solo los activos (active=false para ver los desactivados). "
            + "needsReview=true filtra los creados desde Shopify a los que falta el precio de compra; query busca por nombre o SKU")
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(required = false) Boolean needsReview,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(active, needsReview, query, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'DELIVERY')")
    @Operation(summary = "Obtener producto", description = "Obtiene un producto por ID")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Crear producto", description = "Crea un nuevo producto")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Actualizar producto", description = "Actualiza un producto existente")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable UUID id,
            @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Ajustar stock", description = "Suma (delta positivo) o resta (delta negativo) unidades al stock actual. "
            + "Usar esto en lugar de enviar stock en el PUT, que reemplaza el valor y puede pisar ventas recientes")
    public ResponseEntity<ProductResponse> adjustStock(@PathVariable UUID id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(productService.adjustStock(id, request.delta()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Desactivar producto", description = "Desactiva un producto (soft delete)")
    public ResponseEntity<Void> deactivateProduct(@PathVariable UUID id) {
        productService.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }
}
