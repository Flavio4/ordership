package com.rtz.ordership.repository;

import com.rtz.ordership.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    @EntityGraph(attributePaths = {"order", "user"})
    Page<StockMovement> findByProductId(UUID productId, Pageable pageable);
}
