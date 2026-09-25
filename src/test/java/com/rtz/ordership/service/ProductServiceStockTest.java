package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.ProductResponse;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProductServiceStockTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductService(productRepository);
    }

    @Test
    void adjustStockAddsTheDeltaInTheDatabaseAndReturnsTheCurrentValue() {
        UUID id = UUID.randomUUID();
        when(productRepository.adjustStock(id, -3)).thenReturn(1);
        when(productRepository.findById(id)).thenReturn(Optional.of(product(id, 7)));

        ProductResponse response = productService.adjustStock(id, -3);

        verify(productRepository).adjustStock(id, -3);
        verify(productRepository, never()).save(any());
        assertThat(response.stock()).isEqualTo(7);
    }

    @Test
    void adjustStockRejectsZero() {
        assertThatThrownBy(() -> productService.adjustStock(UUID.randomUUID(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productRepository, never()).adjustStock(any(), anyInt());
    }

    @Test
    void adjustStockOfUnknownProductIsNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.adjustStock(eq(id), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> productService.adjustStock(id, 5))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Product product(UUID id, int stock) {
        return Product.builder()
                .id(id)
                .name("Remera")
                .salePrice(new BigDecimal("150000"))
                .stock(stock)
                .active(true)
                .build();
    }
}
