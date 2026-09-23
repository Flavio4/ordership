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

    public ShopifyWebhookController(ShopifyWebhookService shopifyWebhookService) {
        this.shopifyWebhookService = shopifyWebhookService;
    }

    @PostMapping("/orders")
    public ResponseEntity<Void> handleOrderCreated(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Shopify-Hmac-Sha256", required = false) String hmacHeader) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("SHOPIFY_WEBHOOK_SECRET no configurado - se acepta el webhook sin verificar firma");
        } else if (hmacHeader == null || !isValidSignature(rawBody, hmacHeader)) {
            log.warn("Webhook de Shopify rechazado: firma HMAC inválida o ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        shopifyWebhookService.processOrderCreated(rawBody);
        return ResponseEntity.ok().build();
    }

    private boolean isValidSignature(String rawBody, String hmacHeader) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
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
