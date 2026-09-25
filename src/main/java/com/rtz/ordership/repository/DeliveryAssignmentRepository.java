package com.rtz.ordership.repository;

import com.rtz.ordership.entity.DeliveryAssignment;
import com.rtz.ordership.entity.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {

    @Query("""
            SELECT d FROM DeliveryAssignment d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:zoneId IS NULL OR d.zone.id = :zoneId)
              AND (:carrierId IS NULL OR d.carrier.id = :carrierId)
            """)
    Page<DeliveryAssignment> search(@Param("status") DeliveryStatus status,
            @Param("zoneId") UUID zoneId,
            @Param("carrierId") UUID carrierId,
            Pageable pageable);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<DeliveryStatus> statuses);

    List<DeliveryAssignment> findByOrderIdAndStatusIn(UUID orderId, Collection<DeliveryStatus> statuses);

    List<DeliveryAssignment> findByStatusIn(Collection<DeliveryStatus> statuses);
}
