package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ShopifyWebhookFailureResponse;
import com.rtz.ordership.entity.ShopifyWebhookFailure;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Guarda los pedidos de Shopify que no se pudieron crear porque Shopify mandó datos incompletos
 * (sin teléfono, ítem sin SKU, moneda no soportada...), para no perder el payload de un pedido ya pagado.
 */
@Slf4j
@Service
public class ShopifyWebhookFailureService {

    private final ShopifyWebhookFailureRepository failureRepository;

    public ShopifyWebhookFailureService(ShopifyWebhookFailureRepository failureRepository) {
        this.failureRepository = failureRepository;
    }

    public void recordFailure(String shopifyOrderId, String payload, String reason) {
        // Si Shopify reenvía un pedido que ya falló, se actualiza ese mismo registro
        ShopifyWebhookFailure failure = (shopifyOrderId == null ? null
                : failureRepository.findByShopifyOrderId(shopifyOrderId).orElse(null));

        if (failure == null) {
            failure = ShopifyWebhookFailure.builder()
                    .shopifyOrderId(shopifyOrderId)
                    .payload(payload)
                    .reason(reason)
                    .build();
        } else {
            failure.setPayload(payload);
            failure.setReason(reason);
            failure.setAttempts(failure.getAttempts() + 1);
            failure.setLastAttemptAt(Instant.now());
        }

        failureRepository.save(failure);
        log.error("Pedido Shopify {} no se pudo crear, payload guardado en shopify_webhook_failures: {}",
                shopifyOrderId, reason);
    }

    public List<ShopifyWebhookFailureResponse> getFailures() {
        return failureRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ShopifyWebhookFailureResponse::fromEntity)
                .toList();
    }
}
