package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.enums.OrderSource;
import com.rtz.ordership.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAll(Pageable pageable);

    boolean existsByShopifyOrderId(String shopifyOrderId);

    Optional<Order> findByShopifyOrderId(String shopifyOrderId);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("""
            SELECT o FROM Order o
            WHERE (:customerId IS NULL OR o.customer.id = :customerId)
              AND (:status IS NULL OR o.status = :status)
              AND (:deliveryDate IS NULL OR o.deliveryDate = :deliveryDate)
              AND (:source IS NULL OR o.source = :source)
            """)
    Page<Order> search(@Param("customerId") UUID customerId,
            @Param("status") OrderStatus status,
            @Param("deliveryDate") LocalDate deliveryDate,
            @Param("source") OrderSource source,
            Pageable pageable);

    // Dashboard queries
    long countByStatusAndCreatedAtBetween(OrderStatus status, Instant from, Instant to);

    long countByStatus(OrderStatus status);

    // Ingresos = lo que pagan los clientes (amountToCollect), no la suma a precios de catálogo
    @Query("SELECT COALESCE(SUM(o.amountToCollect), 0) FROM Order o WHERE o.createdAt BETWEEN :from AND :to AND o.status <> :excludedStatus")
    BigDecimal sumAmountToCollectByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to,
            @Param("excludedStatus") OrderStatus excludedStatus);

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :from AND :to GROUP BY o.status")
    List<Object[]> countByStatusGroupedAndCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);
}
