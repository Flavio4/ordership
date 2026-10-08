package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.ProductRequest;
import com.rtz.ordership.dto.response.ProductResponse;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.enums.Currency;
import com.rtz.ordership.entity.enums.StockMovementType;
import com.rtz.ordership.entity.enums.Unit;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.OrderItemRepository;
import com.rtz.ordership.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class ProductServiceStockTest {

    private ProductRepository productRepository;
    private StockMovementService stockMovements;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        stockMovements = mock(StockMovementService.class);
        productService = new ProductService(productRepository, mock(OrderItemRepository.class), stockMovements);
    }

    @Test
    void adjustStockAddsTheDeltaInTheDatabaseAndReturnsTheCurrentValue() {
        UUID id = UUID.randomUUID();
        when(productRepository.adjustStock(id, -3)).thenReturn(1);
        when(productRepository.findById(id)).thenReturn(Optional.of(product(id, 7)));

        ProductResponse response = productService.adjustStock(id, -3, null);

        verify(productRepository).adjustStock(id, -3);
        verify(productRepository, never()).save(any());
        assertThat(response.stock()).isEqualTo(7);
    }

    @Test
    void anAdjustmentGoesToTheHistoryWithItsReason() {
        UUID id = UUID.randomUUID();
        Product product = product(id, 17);
        when(productRepository.adjustStock(id, 12)).thenReturn(1);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        productService.adjustStock(id, 12, "  Llegó mercadería ");
        productService.adjustStock(id, 12, " ");

        verify(stockMovements).record(product, 12, StockMovementType.ADJUSTMENT, null, "Llegó mercadería");
        verify(stockMovements).record(product, 12, StockMovementType.ADJUSTMENT, null, null);
    }

    @Test
    void adjustStockRejectsZero() {
        assertThatThrownBy(() -> productService.adjustStock(UUID.randomUUID(), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(productRepository, never()).adjustStock(any(), anyInt());
        verifyNoInteractions(stockMovements);
    }

    @Test
    void adjustStockOfUnknownProductIsNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.adjustStock(eq(id), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> productService.adjustStock(id, 5, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theInitialStockIsTheFirstMovement() {
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(new ProductRequest("Miel", null, new BigDecimal("30000"),
                new BigDecimal("50000"), Unit.UNID, Currency.PYG, 12, null));

        verify(stockMovements).record(argThat(p -> p.getName().equals("Miel")), eq(12),
                eq(StockMovementType.INITIAL), isNull(), isNull());
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
