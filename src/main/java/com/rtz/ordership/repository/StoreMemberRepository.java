package com.rtz.ordership.repository;

import com.rtz.ordership.entity.StoreMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreMemberRepository extends JpaRepository<StoreMember, UUID> {

    @Query("""
            SELECT m FROM StoreMember m JOIN FETCH m.store s
            WHERE m.user.id = :userId AND s.active = true
            ORDER BY s.name
            """)
    List<StoreMember> findActiveByUserId(@Param("userId") UUID userId);

    Optional<StoreMember> findByStoreIdAndUserId(UUID storeId, UUID userId);
}
