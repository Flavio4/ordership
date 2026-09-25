package com.rtz.ordership.entity;

import com.rtz.ordership.entity.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Un intento de entrega de un pedido. Si falla, el pedido puede tener otro intento con otro repartidor. */
@Entity
@Table(name = "delivery_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrier_id", nullable = false)
    private Carrier carrier;

    // Zona de la dirección del pedido; null en envíos por courier sin dirección zonificada
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.ASSIGNED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        this.assignedAt = Instant.now();
    }

    public boolean isActive() {
        return status == DeliveryStatus.ASSIGNED || status == DeliveryStatus.IN_TRANSIT;
    }
}
