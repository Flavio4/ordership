package com.rtz.ordership.service;

import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.OrderRepository;
import com.rtz.ordership.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderServiceListTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_5 = LocalDate.of(2026, 9, 5);

    private OrderRepository orderRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        when(orderRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any())).thenReturn(Page.empty());
        orderService = new OrderService(orderRepository, mock(CustomerRepository.class),
                mock(CustomerAddressRepository.class), mock(ProductRepository.class));
    }

    @Test
    void theRangeFiltersByCreationDayInParaguay() {
        orderService.getAllOrders(null, null, null, null, null, false, false, SEP_1, SEP_5, PageRequest.of(0, 20));

        // Del 1 a las 00:00 al 6 a las 00:00 en Paraguay (UTC-3)
        verify(orderRepository).search(isNull(), isNull(), isNull(),
                eq(LocalDate.of(2000, 1, 1)), eq(LocalDate.of(9999, 12, 31)),
                eq(Instant.parse("2026-09-01T03:00:00Z")), eq(Instant.parse("2026-09-06T03:00:00Z")),
                isNull(), isNull(), isNull(), isNull(), eq(false), eq(false), any(), any());
    }

    @Test
    void inTheAgendaTheRangeFiltersByDeliveryDate() {
        orderService.getAllOrders(null, null, null, null, null, true, false, SEP_1, null, PageRequest.of(0, 20));

        verify(orderRepository).search(isNull(), isNull(), isNull(), eq(SEP_1), eq(LocalDate.of(9999, 12, 31)),
                eq(Instant.EPOCH), eq(Instant.parse("9999-12-31T00:00:00Z")),
                isNull(), isNull(), isNull(), isNull(), eq(true), eq(false), any(), any());
    }

    @Test
    void fromAfterToIsRejected() {
        assertThatThrownBy(() -> orderService.getAllOrders(null, null, null, null, null, false, false,
                SEP_5, SEP_1, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
