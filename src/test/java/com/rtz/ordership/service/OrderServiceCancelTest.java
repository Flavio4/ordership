package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.OrderStatusUpdateRequest;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.OrderItem;
import com.rtz.ordership.entity.Product;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceCancelTest {

    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        orderService = new OrderService(orderRepository, mock(CustomerRepository.class),
                mock(CustomerAddressRepository.class), mock(ProductRepository.class));
    }

    @Test
    void cancellingThroughStatusUpdateRestoresStock() {
        Product product = product(-2);
        Order order = order(OrderStatus.CONFIRMED, product, 3);

        orderService.updateOrderStatus(order.getId(), new OrderStatusUpdateRequest(OrderStatus.CANCELLED));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void otherStatusUpdatesDoNotTouchStock() {
        Product product = product(5);
        Order order = order(OrderStatus.PENDING, product, 3);

        orderService.updateOrderStatus(order.getId(), new OrderStatusUpdateRequest(OrderStatus.CONFIRMED));

        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void deliveredOrderCannotBeCancelledNorRestoreStock() {
        Product product = product(5);
        Order order = order(OrderStatus.DELIVERED, product, 3);

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(),
                new OrderStatusUpdateRequest(OrderStatus.CANCELLED)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void cancelOrderRestoresStock() {
        Product product = product(0);
        Order order = order(OrderStatus.PENDING, product, 2);

        orderService.cancelOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStock()).isEqualTo(2);
    }

    private Product product(int stock) {
        return Product.builder()
                .id(UUID.randomUUID())
                .name("Remera")
                .salePrice(new BigDecimal("150000"))
                .stock(stock)
                .active(true)
                .build();
    }

    private Order order(OrderStatus status, Product product, int quantity) {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(Customer.builder().id(UUID.randomUUID()).fullName("Cliente").phone("+595981000999").build())
                .status(status)
                .build();
        order.getItems().add(OrderItem.builder().product(product).quantity(quantity).build());
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
}
