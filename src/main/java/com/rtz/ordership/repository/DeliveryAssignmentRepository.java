package com.rtz.ordership.repository;

import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    Page<DeliveryAssignment> findByStatus(DeliveryStatus status, Pageable pageable);

    Page<DeliveryAssignment> findByZoneId(UUID zoneId, Pageable pageable);

    Page<DeliveryAssignment> findByDeliveryUserId(UUID deliveryUserId, Pageable pageable);

    Page<DeliveryAssignment> findByStatusAndZoneId(DeliveryStatus status, UUID zoneId, Pageable pageable);

    Page<DeliveryAssignment> findByStatusAndDeliveryUserId(DeliveryStatus status, UUID deliveryUserId,
            Pageable pageable);

    Page<DeliveryAssignment> findByDeliveryUserIdAndZoneId(UUID deliveryUserId, UUID zoneId, Pageable pageable);

    Page<DeliveryAssignment> findByStatusAndDeliveryUserIdAndZoneId(DeliveryStatus status, UUID deliveryUserId,
            UUID zoneId, Pageable pageable);

    boolean existsByOrderId(UUID orderId);
}
