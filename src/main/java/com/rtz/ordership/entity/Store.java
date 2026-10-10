package com.rtz.ordership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Un negocio que usa OrderShip. Sus datos llevan store_id (ver {@link org.hibernate.annotations.TenantId}). */
@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    // Dominio en Shopify (ej. "mitienda.myshopify.com"): dice a qué tienda va cada webhook
    @Column(unique = true)
    private String shopifyShopDomain;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
