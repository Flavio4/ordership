package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.PaymentStatusUpdateRequest;
import com.rtz.ordership.dto.response.OrderResponse;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.entity.Order;
import com.rtz.ordership.entity.enums.OrderStatus;
import com.rtz.ordership.entity.enums.PaymentMethod;
import com.rtz.ordership.entity.enums.PaymentStatus;
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

class OrderServicePaymentTest {

    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        orderService = new OrderService(orderRepository, mock(CustomerRepository.class),
                mock(CustomerAddressRepository.class), mock(ProductRepository.class),
                mock(StockMovementService.class));
    }

    @Test
    void markingPaidSavesHowTheCustomerPaid() {
        Order order = order();

        OrderResponse paid = orderService.updatePaymentStatus(order.getId(),
                new PaymentStatusUpdateRequest(PaymentStatus.PAID, PaymentMethod.TRANSFER));

        assertThat(paid.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.paymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
    }

    @Test
    void backToUnpaidClearsThePaymentMethod() {
        Order order = order();
        orderService.updatePaymentStatus(order.getId(), new PaymentStatusUpdateRequest(PaymentStatus.PARTIAL, PaymentMethod.CASH));

        orderService.updatePaymentStatus(order.getId(), new PaymentStatusUpdateRequest(PaymentStatus.UNPAID, PaymentMethod.CASH));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getPaymentMethod()).isNull();
    }

    @Test
    void withoutAMethodThePreviousOneIsKept() {
        Order order = order();
        orderService.updatePaymentStatus(order.getId(), new PaymentStatusUpdateRequest(PaymentStatus.PARTIAL, PaymentMethod.CASH));

        orderService.updatePaymentStatus(order.getId(), new PaymentStatusUpdateRequest(PaymentStatus.PAID, null));

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
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
