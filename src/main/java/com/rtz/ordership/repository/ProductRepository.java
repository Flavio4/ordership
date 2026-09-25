package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByActiveTrue();

    @Query("""
            SELECT p FROM Product p
            WHERE p.active = :active
              AND (:needsReview IS NULL OR p.needsReview = :needsReview)
              AND (:textLike IS NULL
                   OR LOWER(p.name) LIKE :textLike
                   OR LOWER(p.shopifySku) LIKE :textLike)
            """)
    Page<Product> search(@Param("active") boolean active,
            @Param("needsReview") Boolean needsReview,
            @Param("textLike") String textLike,
            Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :delta WHERE p.id = :id")
    int adjustStock(@Param("id") UUID id, @Param("delta") int delta);

    boolean existsByName(String name);

    boolean existsByShopifySku(String shopifySku);

    Optional<Product> findByShopifySku(String shopifySku);

    Optional<Product> findFirstByNameIgnoreCaseAndShopifySkuIsNull(String name);
}
