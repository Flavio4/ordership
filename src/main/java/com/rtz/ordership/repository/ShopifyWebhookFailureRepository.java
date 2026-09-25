package com.rtz.ordership.repository;

import com.rtz.ordership.entity.ShopifyWebhookFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopifyWebhookFailureRepository extends JpaRepository<ShopifyWebhookFailure, UUID> {

    Optional<ShopifyWebhookFailure> findByShopifyOrderId(String shopifyOrderId);

    List<ShopifyWebhookFailure> findByResolvedAtIsNullOrderByCreatedAtDesc();

    List<ShopifyWebhookFailure> findByResolvedAtIsNotNullOrderByCreatedAtDesc();
}
