package com.rtz.ordership.controller;

import com.rtz.ordership.dto.request.ResolveWebhookFailureRequest;
import com.rtz.ordership.dto.response.ShopifyWebhookFailureResponse;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.service.ShopifyWebhookFailureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Fuera de /api/webhooks/shopify a propósito: esa ruta acepta POST sin autenticación.
@RestController
@RequestMapping("/api/shopify/webhook-failures")
@Tag(name = "Fallos de Shopify", description = "Pedidos de Shopify que no se pudieron crear")
public class ShopifyWebhookFailureController {

    private final ShopifyWebhookFailureService failureService;

    public ShopifyWebhookFailureController(ShopifyWebhookFailureService failureService) {
        this.failureService = failureService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Listar fallos",
            description = "Pedidos de Shopify que no se crearon por datos incompletos (sin teléfono, ítem sin SKU, "
                    + "moneda no soportada), del más reciente al más antiguo. Incluye el payload original "
                    + "para cargar el pedido a mano. Por defecto solo los pendientes; resolved=true para ver los resueltos")
    public ResponseEntity<Page<ShopifyWebhookFailureResponse>> getFailures(
            @RequestParam(defaultValue = "false") boolean resolved,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(failureService.getFailures(resolved, pageable));
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Marcar fallo como resuelto",
            description = "Marca el fallo como atendido con una nota (ej. \"cargado a mano\"). No crea el pedido")
    public ResponseEntity<ShopifyWebhookFailureResponse> resolve(@PathVariable UUID id,
            @Valid @RequestBody ResolveWebhookFailureRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(failureService.resolve(id, request.note(), user));
    }
}
