package com.rtz.ordership.repository;

import com.rtz.ordership.entity.ShopifyWebhookFailure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShopifyWebhookFailureRepository extends JpaRepository<ShopifyWebhookFailure, UUID> {

    Optional<ShopifyWebhookFailure> findByShopifyOrderId(String shopifyOrderId);

    Page<ShopifyWebhookFailure> findByResolvedAtIsNull(Pageable pageable);

    Page<ShopifyWebhookFailure> findByResolvedAtIsNotNull(Pageable pageable);
}
