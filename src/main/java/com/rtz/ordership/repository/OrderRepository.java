package com.rtz.ordership.repository;

import com.rtz.ordership.dto.response.CustomerOrderStats;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAll(Pageable pageable);

    // Shopify_OrderId = order.shopify.orderId (campo del objeto embebido ShopifyReference)
    boolean existsByShopify_OrderId(String shopifyOrderId);

    Optional<Order> findByShopify_OrderId(String shopifyOrderId);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            WHERE (:customerId IS NULL OR o.customer.id = :customerId)
              AND (:status IS NULL OR o.status = :status)
              AND (:deliveryDate IS NULL OR o.deliveryDate = :deliveryDate)
              AND o.createdAt >= :createdFrom
              AND o.createdAt < :createdBefore
              AND (:source IS NULL OR o.source = :source)
              AND (:textLike IS NULL
                   OR LOWER(o.customer.fullName) LIKE :textLike
                   OR LOWER(o.shopify.orderName) LIKE :textLike
                   OR (:phoneLike IS NOT NULL AND o.customer.phone LIKE :phoneLike)
                   OR (:orderNumber IS NOT NULL AND o.orderNumber = :orderNumber))
              AND (:scheduled = false
                   OR (o.deliveryDate IS NOT NULL
                       AND o.deliveryDate BETWEEN :deliveryFrom AND :deliveryTo
                       AND o.status IN (com.rtz.ordership.entity.enums.OrderStatus.CONFIRMED,
                                        com.rtz.ordership.entity.enums.OrderStatus.ASSIGNED,
                                        com.rtz.ordership.entity.enums.OrderStatus.IN_TRANSIT)))
              AND (:missingDeliveryCost = false
                   OR (o.status = com.rtz.ordership.entity.enums.OrderStatus.DELIVERED
                       AND o.deliveryCost IS NULL
                       AND o.createdAt >= :missingDeliveryCostSince))
            """)
    Page<Order> search(@Param("customerId") UUID customerId,
            @Param("status") OrderStatus status,
            @Param("deliveryDate") LocalDate deliveryDate,
            @Param("deliveryFrom") LocalDate deliveryFrom,
            @Param("deliveryTo") LocalDate deliveryTo,
            @Param("createdFrom") Instant createdFrom,
            @Param("createdBefore") Instant createdBefore,
            @Param("source") OrderSource source,
            @Param("textLike") String textLike,
            @Param("phoneLike") String phoneLike,
            @Param("orderNumber") Long orderNumber,
            @Param("scheduled") boolean scheduled,
            @Param("missingDeliveryCost") boolean missingDeliveryCost,
            @Param("missingDeliveryCostSince") Instant missingDeliveryCostSince,
            Pageable pageable);

    // Agenda: pedidos confirmados (o ya en reparto) con fecha de entrega, todavía sin entregar
    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.deliveryDate < :date
              AND (:source IS NULL OR o.source = :source)
              AND o.status IN (com.rtz.ordership.entity.enums.OrderStatus.CONFIRMED,
                               com.rtz.ordership.entity.enums.OrderStatus.ASSIGNED,
                               com.rtz.ordership.entity.enums.OrderStatus.IN_TRANSIT)
            """)
    long countScheduledBefore(@Param("date") LocalDate date, @Param("source") OrderSource source);

    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.deliveryDate = :date
              AND (:source IS NULL OR o.source = :source)
              AND o.status IN (com.rtz.ordership.entity.enums.OrderStatus.CONFIRMED,
                               com.rtz.ordership.entity.enums.OrderStatus.ASSIGNED,
                               com.rtz.ordership.entity.enums.OrderStatus.IN_TRANSIT)
            """)
    long countScheduledOn(@Param("date") LocalDate date, @Param("source") OrderSource source);

    // Dashboard
    long countByStatusIn(Collection<OrderStatus> statuses);

    /**
     * Un pedido por fila, sin los cancelados: [createdAt, amountToCollect, costo de productos, costo del delivery,
     * ítems sin costo, estado del pago]. Ventas = lo que pagan los clientes (amountToCollect), no la suma a precios de
     * catálogo.
     */
    @Query("""
            SELECT o.createdAt, o.amountToCollect,
                   COALESCE(SUM(i.quantity * i.unitCost), 0),
                   COALESCE(o.deliveryCost, 0),
                   SUM(CASE WHEN i.id IS NOT NULL AND i.unitCost IS NULL THEN 1 ELSE 0 END),
                   o.paymentStatus
            FROM Order o LEFT JOIN o.items i
            WHERE o.createdAt >= :from
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            GROUP BY o.id, o.createdAt, o.amountToCollect, o.deliveryCost, o.paymentStatus
            """)
    List<Object[]> salesSince(@Param("from") Instant from);

    /**
     * Totales de los pedidos no cancelados creados en [from, before), para el resumen por período:
     * [pedidos, vendido, costo del delivery, pedidos pagados, cobrado, costo del delivery de los pagados,
     * primer pedido].
     */
    @Query("""
            SELECT COUNT(o),
                   COALESCE(SUM(o.amountToCollect), 0),
                   COALESCE(SUM(COALESCE(o.deliveryCost, 0)), 0),
                   COALESCE(SUM(CASE WHEN o.paymentStatus = com.rtz.ordership.entity.enums.PaymentStatus.PAID
                                     THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN o.paymentStatus = com.rtz.ordership.entity.enums.PaymentStatus.PAID
                                     THEN o.amountToCollect ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN o.paymentStatus = com.rtz.ordership.entity.enums.PaymentStatus.PAID
                                     THEN COALESCE(o.deliveryCost, 0) ELSE 0 END), 0),
                   MIN(o.createdAt)
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :before
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            """)
    List<Object[]> orderTotals(@Param("from") Instant from, @Param("before") Instant before);

    /**
     * Lo mismo, de los ítems: [costo de los productos, costo de los productos de los pagados,
     * pedidos con algún producto sin costo, pagados con algún producto sin costo].
     */
    @Query("""
            SELECT COALESCE(SUM(i.quantity * i.unitCost), 0),
                   COALESCE(SUM(CASE WHEN o.paymentStatus = com.rtz.ordership.entity.enums.PaymentStatus.PAID
                                     THEN i.quantity * i.unitCost ELSE 0 END), 0),
                   COUNT(DISTINCT CASE WHEN i.unitCost IS NULL THEN o.id END),
                   COUNT(DISTINCT CASE WHEN i.unitCost IS NULL
                                        AND o.paymentStatus = com.rtz.ordership.entity.enums.PaymentStatus.PAID
                                       THEN o.id END)
            FROM OrderItem i JOIN i.order o
            WHERE o.createdAt >= :from AND o.createdAt < :before
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            """)
    List<Object[]> itemTotals(@Param("from") Instant from, @Param("before") Instant before);

    // Entregados sin el costo del delivery cargado (para completar la ganancia)
    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.status = com.rtz.ordership.entity.enums.OrderStatus.DELIVERED
              AND o.deliveryCost IS NULL
              AND o.createdAt >= :since
            """)
    long countDeliveredWithoutCostSince(@Param("since") Instant since);

    @Query("""
            SELECT new com.rtz.ordership.dto.response.CustomerOrderStats(
                o.customer.id, COUNT(o), COALESCE(SUM(o.amountToCollect), 0), MAX(o.createdAt))
            FROM Order o
            WHERE o.customer.id IN :customerIds
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            GROUP BY o.customer.id
            """)
    List<CustomerOrderStats> orderStatsByCustomer(@Param("customerIds") Collection<UUID> customerIds);
}
