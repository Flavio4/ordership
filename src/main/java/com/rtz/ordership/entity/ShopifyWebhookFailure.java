package com.rtz.ordership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shopify_webhook_failures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopifyWebhookFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Puede ser null si el payload ni siquiera se pudo leer
    @Column(unique = true)
    private String shopifyOrderId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 1;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastAttemptAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.lastAttemptAt = this.createdAt;
    }
}
