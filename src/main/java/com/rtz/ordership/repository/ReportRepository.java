package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sumas para los reportes, de los pedidos no cancelados creados en [from, before). Lo que se cobró por cada producto
 * es su subtotal con los descuentos (o los extras de Shopify) del pedido repartidos en proporción, sin el envío:
 * así los productos suman lo mismo que las ventas del dashboard, menos el envío.
 */
public interface ReportRepository extends Repository<Order, UUID> {

    /**
     * Una fila por producto: [id, nombre, unidad, pedidos, unidades, cobrado, cobrado de las unidades con costo,
     * costo, unidades sin costo].
     */
    @Query("""
            SELECT p.id, p.name, p.unit,
                   COUNT(DISTINCT o.id),
                   SUM(i.quantity),
                   SUM(CASE WHEN o.totalAmount > 0
                            THEN i.subtotal * (o.amountToCollect - COALESCE(o.deliveryFee, 0)) / o.totalAmount
                            ELSE i.subtotal END),
                   SUM(CASE WHEN i.unitCost IS NULL THEN 0
                            WHEN o.totalAmount > 0
                            THEN i.subtotal * (o.amountToCollect - COALESCE(o.deliveryFee, 0)) / o.totalAmount
                            ELSE i.subtotal END),
                   COALESCE(SUM(i.quantity * i.unitCost), 0),
                   SUM(CASE WHEN i.unitCost IS NULL THEN i.quantity ELSE 0 END)
            FROM OrderItem i JOIN i.order o JOIN i.product p
            WHERE o.createdAt >= :from AND o.createdAt < :before
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            GROUP BY p.id, p.name, p.unit
            """)
    List<Object[]> productSales(@Param("from") Instant from, @Param("before") Instant before);

    // Una fila por cliente: [id, nombre, teléfono, pedidos, cobrado, último pedido]
    @Query("""
            SELECT c.id, c.fullName, c.phone, COUNT(o), SUM(o.amountToCollect), MAX(o.createdAt)
            FROM Order o JOIN o.customer c
            WHERE o.createdAt >= :from AND o.createdAt < :before
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            GROUP BY c.id, c.fullName, c.phone
            """)
    List<Object[]> customerSales(@Param("from") Instant from, @Param("before") Instant before);

    // Clientes que compraron alguna vez pero no desde :since, con todo lo que compraron: misma forma que customerSales
    @Query("""
            SELECT c.id, c.fullName, c.phone, COUNT(o), SUM(o.amountToCollect), MAX(o.createdAt)
            FROM Order o JOIN o.customer c
            WHERE o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            GROUP BY c.id, c.fullName, c.phone
            HAVING MAX(o.createdAt) < :since
            """)
    List<Object[]> customersWithoutOrdersSince(@Param("since") Instant since);

    // [origen, pedidos, vendido]
    @Query("""
            SELECT o.source, COUNT(o), SUM(o.amountToCollect)
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :before
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            GROUP BY o.source
            """)
    List<Object[]> salesBySource(@Param("from") Instant from, @Param("before") Instant before);

    // Solo lo cobrado (PAID): [forma de pago (null en los pagados antes de que existiera), pedidos, cobrado]
    @Query("""
            SELECT o.paymentMethod, COUNT(o), SUM(o.amountToCollect)
            FROM Order o
            WHERE o.createdAt >= :from AND o.createdAt < :before
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
              AND o.paymentStatus = com.rtz.ordership.entity.enums.PaymentStatus.PAID
            GROUP BY o.paymentMethod
            """)
    List<Object[]> collectedByPaymentMethod(@Param("from") Instant from, @Param("before") Instant before);

    // [zona (null = sin dirección con zona), pedidos, vendido]
    @Query("""
            SELECT z.name, COUNT(o), SUM(o.amountToCollect)
            FROM Order o LEFT JOIN o.customerAddress a LEFT JOIN a.zone z
            WHERE o.createdAt >= :from AND o.createdAt < :before
              AND o.status <> com.rtz.ordership.entity.enums.OrderStatus.CANCELLED
            GROUP BY z.name
            """)
    List<Object[]> salesByZone(@Param("from") Instant from, @Param("before") Instant before);
}
