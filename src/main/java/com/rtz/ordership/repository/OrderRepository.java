package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Order;
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
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAll(Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByDeliveryDate(LocalDate deliveryDate, Pageable pageable);

    Page<Order> findByStatusAndDeliveryDate(OrderStatus status, LocalDate deliveryDate, Pageable pageable);

    // Filtros por cliente
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerIdAndDeliveryDate(UUID customerId, LocalDate deliveryDate, Pageable pageable);

    Page<Order> findByCustomerIdAndStatusAndDeliveryDate(UUID customerId, OrderStatus status, LocalDate deliveryDate,
            Pageable pageable);

    // Dashboard queries
    long countByStatusAndCreatedAtBetween(OrderStatus status, Instant from, Instant to);

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.createdAt BETWEEN :from AND :to AND o.status <> :excludedStatus")
    BigDecimal sumTotalAmountByCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to,
            @Param("excludedStatus") OrderStatus excludedStatus);

    @Query("SELECT o.status, COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :from AND :to GROUP BY o.status")
    List<Object[]> countByStatusGroupedAndCreatedAtBetween(@Param("from") Instant from, @Param("to") Instant to);
}
