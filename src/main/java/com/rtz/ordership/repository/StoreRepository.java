package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByShopifyShopDomainAndActiveTrue(String shopifyShopDomain);
}
