package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    List<Customer> findByActiveTrue();

    boolean existsByPhone(String phone);
}
