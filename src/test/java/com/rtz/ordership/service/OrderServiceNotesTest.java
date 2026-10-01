package com.rtz.ordership.service;

import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Order;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceNotesTest {

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
    void theNoteIsTrimmedAndAnEmptyOneClearsIt() {
        Order order = order();

        orderService.updateNotes(order.getId(), "  Llamar antes de ir  ");
        assertThat(order.getNotes()).isEqualTo("Llamar antes de ir");

        orderService.updateNotes(order.getId(), "   ");
        assertThat(order.getNotes()).isNull();
    }

    private Order order() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customer(Customer.builder().id(UUID.randomUUID()).fullName("Cliente").phone("+595981000999").build())
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.ZERO)
                .amountToCollect(BigDecimal.ZERO)
                .build();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
}
