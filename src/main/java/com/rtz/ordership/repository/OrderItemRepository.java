package com.rtz.ordership.repository;

import com.rtz.ordership.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    // Ítems vendidos sin costo (el producto no tenía precio de compra): toman el que se acaba de cargar
    @Modifying
    @Query("UPDATE OrderItem i SET i.unitCost = :unitCost WHERE i.product.id = :productId AND i.unitCost IS NULL")
    int fillMissingUnitCost(@Param("productId") UUID productId, @Param("unitCost") BigDecimal unitCost);
}
