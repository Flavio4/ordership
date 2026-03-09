package com.rtz.ordership.entity;

import com.rtz.ordership.entity.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_user_id", nullable = false)
    private User deliveryUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.ASSIGNED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt;

    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        this.assignedAt = Instant.now();
    }
}
