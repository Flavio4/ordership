package com.rtz.ordership.controller;

import com.rtz.ordership.service.ShopifyWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/shopify")
public class ShopifyWebhookController {

    private final ShopifyWebhookService shopifyWebhookService;

    @Value("${app.shopify.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.shopify.allow-unsigned-webhooks:false}")
    private boolean allowUnsignedWebhooks;

    public ShopifyWebhookController(ShopifyWebhookService shopifyWebhookService) {
        this.shopifyWebhookService = shopifyWebhookService;
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> handleOrderCreated(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Shopify-Hmac-Sha256", required = false) String hmacHeader) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            if (!allowUnsignedWebhooks) {
                log.error("Webhook de Shopify rechazado: SHOPIFY_WEBHOOK_SECRET no configurado");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            log.warn("SHOPIFY_WEBHOOK_SECRET no configurado - se acepta el webhook sin verificar firma (solo dev)");
        } else if (hmacHeader == null || !isValidSignature(rawBody, hmacHeader)) {
            log.warn("Webhook de Shopify rechazado: firma HMAC inválida o ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Si el pedido no se puede crear por datos incompletos, queda guardado como fallo y se responde 200 igual;
        // solo un error técnico devuelve 500 para que Shopify reintente.
        shopifyWebhookService.receiveOrderCreated(new String(rawBody, StandardCharsets.UTF_8));
        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(byte[] rawBody, String hmacHeader) {
        try {
            // Los webhooks de Shopify firman en Base64 (el hexadecimal es solo para OAuth y app proxies)
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody);
            String computedBase64 = Base64.getEncoder().encodeToString(computed);
            return MessageDigest.isEqual(
                    computedBase64.getBytes(StandardCharsets.UTF_8),
                    hmacHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Error validando firma del webhook de Shopify", e);
            return false;
        }
    }
}
