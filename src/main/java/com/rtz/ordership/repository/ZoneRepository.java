package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    List<Zone> findByActiveTrue();

    boolean existsByName(String name);
}
