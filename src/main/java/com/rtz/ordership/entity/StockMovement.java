package com.rtz.ordership.entity;

import com.rtz.ordership.entity.enums.StockMovementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    // Unidades que entraron (+) o salieron (-)
    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Integer stockAfter;

    // Motivo de un ajuste a mano ("Llegó mercadería", "Rotura")
    private String reason;

    // Pedido que lo movió (venta, edición o cancelación)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Null si lo hizo Shopify
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
