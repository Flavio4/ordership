package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.StockMovementResponse;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.StockMovement;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.StockMovementType;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.repository.StockMovementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Historial de stock. Se llama después de cambiar el stock del producto: guarda la cantidad movida y el stock con
 * que quedó. El usuario es el de la sesión; en los pedidos que entran por el webhook de Shopify queda en null.
 */
@Slf4j
@Service
public class StockMovementService {

    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;

    public StockMovementService(StockMovementRepository movementRepository, ProductRepository productRepository) {
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
    }

    public void record(Product product, int quantity, StockMovementType type, Order order, String reason) {
        if (quantity == 0) return;
        movementRepository.save(StockMovement.builder()
                .product(product)
                .type(type)
                .quantity(quantity)
                .stockAfter(product.getStock())
                .reason(reason)
                .order(order)
                .user(currentUser())
                .build());
        log.info("Movimiento de stock: {} {} ({}), queda {}", product.getName(), quantity, type, product.getStock());
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> list(UUID productId, Pageable pageable) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Producto no encontrado con ID: " + productId);
        }
        return movementRepository.findByProductId(productId, pageable).map(StockMovementResponse::fromEntity);
    }

    private static User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof User user ? user : null;
    }
}
