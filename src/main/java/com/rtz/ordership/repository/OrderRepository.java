package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findAll(Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByDeliveryDate(LocalDate deliveryDate, Pageable pageable);

    Page<Order> findByStatusAndDeliveryDate(OrderStatus status, LocalDate deliveryDate, Pageable pageable);
}
