package com.rtz.ordership.entity;

import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.PaymentStatus;
import com.rtz.ordership.entity.enums.ShippingMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    // Null cuando el pedido llega de Shopify sin una dirección zonificada todavía (ver shippingAddressRaw)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_address_id")
    private CustomerAddress customerAddress;

    // Null en pedidos de Shopify: no los crea un operador logueado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, updatable = false)
    private OrderSource source = OrderSource.MANUAL;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private ShippingMethod shippingMethod = ShippingMethod.OWN_DELIVERY;

    private String courierName;

    private String trackingCode;

    private Instant confirmedAt;

    // Solo en pedidos que vienen de Shopify; null en los manuales
    @Embedded
    private ShopifyReference shopify;

    @Column(columnDefinition = "TEXT")
    private String shippingAddressRaw;

    // Suma de los ítems con los precios del catálogo de OrderShip
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Lo que el cliente debe pagar. En pedidos de Shopify es el total de Shopify (con ofertas y extras);
    // en pedidos manuales, igual a totalAmount
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountToCollect;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDate deliveryDate;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private DeliveryAssignment deliveryAssignment;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
