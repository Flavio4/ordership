package com.rtz.ordership.repository;

import com.rtz.ordership.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByCustomerIdAndActiveTrue(UUID customerId);

    Optional<CustomerAddress> findFirstByCustomerIdAndActiveTrueOrderByCreatedAtDesc(UUID customerId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE CustomerAddress a SET a.isDefault = false
            WHERE a.customer.id = :customerId AND a.id <> :defaultAddressId AND a.isDefault = true
            """)
    int unsetOtherDefaults(@Param("customerId") UUID customerId, @Param("defaultAddressId") UUID defaultAddressId);
}
