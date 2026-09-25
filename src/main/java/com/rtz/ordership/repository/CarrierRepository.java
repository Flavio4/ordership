package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Carrier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarrierRepository extends JpaRepository<Carrier, UUID> {

    List<Carrier> findByActiveTrueOrderByTypeAscNameAsc();

    List<Carrier> findAllByOrderByTypeAscNameAsc();

    Optional<Carrier> findByUserId(UUID userId);

    boolean existsByUserIdAndIdNot(UUID userId, UUID id);

    boolean existsByUserId(UUID userId);
}
