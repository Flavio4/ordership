package com.rtz.ordership.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_discounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Motivo opcional: "Cliente frecuente", "Producto golpeado"…
    private String label;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // Orden en que se cargaron
    @Column(nullable = false)
    private Integer position;
}
