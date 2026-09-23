package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByActiveTrue();

    Page<Customer> findByActiveTrue(Pageable pageable);

    Page<Customer> findByActiveTrueAndFullNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsByPhone(String phone);

    Optional<Customer> findByPhone(String phone);
}
