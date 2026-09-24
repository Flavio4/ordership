package com.rtz.ordership.controller;

import com.rtz.ordership.dto.response.ShopifyWebhookFailureResponse;
import com.rtz.ordership.service.ShopifyWebhookFailureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
                    + "para cargar el pedido a mano")
    public ResponseEntity<List<ShopifyWebhookFailureResponse>> getFailures() {
        return ResponseEntity.ok(failureService.getFailures());
    }
}
