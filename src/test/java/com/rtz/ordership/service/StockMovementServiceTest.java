package com.rtz.ordership.service;

import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.StockMovement;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.StockMovementType;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.repository.StockMovementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class StockMovementServiceTest {

    private StockMovementRepository movementRepository;
    private ProductRepository productRepository;
    private StockMovementService service;
    private final Product product = Product.builder().id(UUID.randomUUID()).name("Miel").stock(4).build();

    @BeforeEach
    void setUp() {
        movementRepository = mock(StockMovementRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new StockMovementService(movementRepository, productRepository);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aMovementKeepsTheStockItLeftAndWhoDidIt() {
        User operator = User.builder().id(UUID.randomUUID()).fullName("Operador").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(operator, null, List.of()));
        Order order = Order.builder().id(UUID.randomUUID()).build();

        service.record(product, -2, StockMovementType.SALE, order, null);

        StockMovement saved = captureSaved();
        assertThat(saved.getQuantity()).isEqualTo(-2);
        assertThat(saved.getStockAfter()).isEqualTo(4);
        assertThat(saved.getType()).isEqualTo(StockMovementType.SALE);
        assertThat(saved.getOrder()).isSameAs(order);
        assertThat(saved.getUser()).isSameAs(operator);
    }

    @Test
    void aShopifyWebhookHasNoUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        service.record(product, -1, StockMovementType.SALE, null, null);

        assertThat(captureSaved().getUser()).isNull();
    }

    @Test
    void zeroIsNotAMovement() {
        service.record(product, 0, StockMovementType.ORDER_EDIT, null, null);

        verifyNoInteractions(movementRepository);
    }

    @Test
    void theHistoryOfAnUnknownProductIsNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.list(id, Pageable.unpaged()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private StockMovement captureSaved() {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository).save(captor.capture());
        return captor.getValue();
    }
}
