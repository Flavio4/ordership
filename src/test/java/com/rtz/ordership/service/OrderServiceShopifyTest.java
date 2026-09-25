package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.OrderItemRequest;
import com.rtz.ordership.dto.webhook.ShopifyOrderDetails;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Order;
import org.mockito.ArgumentCaptor;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceShopifyTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        orderService = new OrderService(orderRepository, mock(CustomerRepository.class),
                mock(CustomerAddressRepository.class), productRepository);
    }

    @Test
    void shopifyOrderIsCreatedEvenIfStockGoesNegative() {
        Product product = product(1, true);

        orderService.createOrderFromShopify(customer(), details("555", null), List.of(new OrderItemRequest(product.getId(), 3)));

        assertThat(product.getStock()).isEqualTo(-2);
        verify(orderRepository).save(any());
    }

    @Test
    void shopifyOrderIsCreatedEvenIfProductIsDeactivated() {
        Product product = product(5, false);

        orderService.createOrderFromShopify(customer(), details("556", null), List.of(new OrderItemRequest(product.getId(), 1)));

        assertThat(product.getStock()).isEqualTo(4);
        verify(orderRepository).save(any());
    }

    @Test
    void amountToCollectIsTheShopifyTotalWhileTotalAmountUsesCatalogPrices() {
        Product product = product(10, true); // precio de catálogo 150.000

        orderService.createOrderFromShopify(customer(), details("557", new BigDecimal("255000")), List.of(new OrderItemRequest(product.getId(), 2)));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualByComparingTo("300000");
        assertThat(saved.getValue().getAmountToCollect()).isEqualByComparingTo("255000");
        assertThat(saved.getValue().getShopify().getOrderId()).isEqualTo("557");
        assertThat(saved.getValue().getShopify().getOrderName()).isEqualTo("#1557");
    }

    @Test
    void amountToCollectFallsBackToCatalogTotalWhenShopifyTotalIsMissing() {
        Product product = product(10, true);

        orderService.createOrderFromShopify(customer(), details("558", null), List.of(new OrderItemRequest(product.getId(), 2)));

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getAmountToCollect()).isEqualByComparingTo("300000");
    }

    private ShopifyOrderDetails details(String shopifyOrderId, BigDecimal amountToCollect) {
        return new ShopifyOrderDetails(shopifyOrderId, "#1" + shopifyOrderId, null, "Asunción", amountToCollect);
    }

    private Product product(int stock, boolean active) {
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Remera")
                .salePrice(new BigDecimal("150000"))
                .stock(stock)
                .active(active)
                .build();
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        return product;
    }

    private Customer customer() {
        return Customer.builder().id(UUID.randomUUID()).fullName("Cliente").phone("+595981000999").build();
    }
}
