package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.ShopifyWebhookFailure;

import java.time.Instant;
import java.util.UUID;

public record ShopifyWebhookFailureResponse(
        UUID id,
        String shopifyOrderId,
        String reason,
        Integer attempts,
        Instant createdAt,
        Instant lastAttemptAt,
        Instant resolvedAt,
        String resolvedByName,
        String resolutionNote,
        String payload) {
    public static ShopifyWebhookFailureResponse fromEntity(ShopifyWebhookFailure failure) {
        if (failure == null)
            return null;
        return new ShopifyWebhookFailureResponse(
                failure.getId(),
                failure.getShopifyOrderId(),
                failure.getReason(),
                failure.getAttempts(),
                failure.getCreatedAt(),
                failure.getLastAttemptAt(),
                failure.getResolvedAt(),
                failure.getResolvedBy() != null ? failure.getResolvedBy().getFullName() : null,
                failure.getResolutionNote(),
                failure.getPayload());
    }
}
