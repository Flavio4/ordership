package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ShopifyWebhookFailureResponse;
import com.rtz.ordership.entity.ShopifyWebhookFailure;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.ShopifyWebhookFailureRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public List<ShopifyWebhookFailureResponse> getFailures(boolean resolved) {
        List<ShopifyWebhookFailure> failures = resolved
                ? failureRepository.findByResolvedAtIsNotNullOrderByCreatedAtDesc()
                : failureRepository.findByResolvedAtIsNullOrderByCreatedAtDesc();
        return failures.stream()
                .map(ShopifyWebhookFailureResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ShopifyWebhookFailureResponse resolve(UUID id, String note, User user) {
        ShopifyWebhookFailure failure = failureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fallo de Shopify no encontrado con ID: " + id));
        if (failure.getResolvedAt() != null) {
            throw new IllegalStateException("El fallo ya fue marcado como resuelto");
        }

        failure.setResolvedAt(Instant.now());
        failure.setResolvedBy(user);
        failure.setResolutionNote(note.trim());
        failure = failureRepository.save(failure);
        log.info("Fallo de Shopify {} (pedido {}) marcado como resuelto por {}",
                id, failure.getShopifyOrderId(), user.getEmail());
        return ShopifyWebhookFailureResponse.fromEntity(failure);
    }
}
