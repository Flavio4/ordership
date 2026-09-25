package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByActiveTrue();

    @Query("""
            SELECT c FROM Customer c
            WHERE c.active = true
              AND (:textLike IS NULL
                   OR LOWER(c.fullName) LIKE :textLike
                   OR (:phoneLike IS NOT NULL AND c.phone LIKE :phoneLike))
            """)
    Page<Customer> searchActive(@Param("textLike") String textLike,
            @Param("phoneLike") String phoneLike,
            Pageable pageable);

    boolean existsByPhone(String phone);

    Optional<Customer> findByPhone(String phone);
}
