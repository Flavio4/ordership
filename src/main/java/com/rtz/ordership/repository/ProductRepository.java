package com.rtz.ordership.repository;

import com.rtz.ordership.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByActiveTrue();

    boolean existsByName(String name);
}
