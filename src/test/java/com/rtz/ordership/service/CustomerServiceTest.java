package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.CustomerUpdateRequest;
import com.rtz.ordership.dto.response.CustomerDetailResponse;
import com.rtz.ordership.dto.response.CustomerOrderStats;
import com.rtz.ordership.entity.Customer;
import com.rtz.ordership.repository.CustomerRepository;
import com.rtz.ordership.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CustomerServiceTest {

    private CustomerRepository customerRepository;
    private OrderRepository orderRepository;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        orderRepository = mock(OrderRepository.class);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new CustomerService(customerRepository, mock(CustomerAddressService.class), orderRepository);
    }

    @Test
    void theListIncludesEachCustomersPurchaseSummary() {
        Customer frequent = customer("Ana");
        Customer newOne = customer("Beto");
        Instant last = Instant.parse("2026-09-24T15:00:00Z");
        when(customerRepository.searchActive(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(frequent, newOne), PageRequest.of(0, 20), 2));
        when(orderRepository.orderStatsByCustomer(List.of(frequent.getId(), newOne.getId())))
                .thenReturn(List.of(new CustomerOrderStats(frequent.getId(), 3, new BigDecimal("750000"), last)));

        List<CustomerDetailResponse> customers = service.getAllCustomers(null, PageRequest.of(0, 20)).getContent();

        assertThat(customers.get(0).orderCount()).isEqualTo(3);
        assertThat(customers.get(0).totalSpent()).isEqualByComparingTo("750000");
        assertThat(customers.get(0).lastOrderAt()).isEqualTo(last);
        assertThat(customers.get(1).orderCount()).isZero();
        assertThat(customers.get(1).totalSpent()).isEqualByComparingTo("0");
        assertThat(customers.get(1).lastOrderAt()).isNull();
    }

    @Test
    void emptyTextsClearEmailAndNotesAndABlankNameIsRejected() {
        Customer customer = customer("Ana");
        customer.setEmail("ana@mail.com");
        customer.setNotes("Toca timbre");
        when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        service.updateCustomer(customer.getId(), new CustomerUpdateRequest(null, null, "", " ", null));
        assertThat(customer.getEmail()).isNull();
        assertThat(customer.getNotes()).isNull();

        assertThatThrownBy(() -> service.updateCustomer(customer.getId(),
                new CustomerUpdateRequest("  ", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(customer.getFullName()).isEqualTo("Ana");
    }

    private Customer customer(String name) {
        return Customer.builder().id(UUID.randomUUID()).fullName(name).phone("+59598100" + name.length()).active(true).build();
    }
}
