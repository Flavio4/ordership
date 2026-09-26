package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerDetailResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        String notes,
        Boolean active,
        Instant createdAt,
        List<CustomerAddressResponse> addresses,
        long orderCount,
        BigDecimal totalSpent,
        Instant lastOrderAt) {
    public static CustomerDetailResponse fromEntity(Customer customer, CustomerOrderStats stats) {
        return new CustomerDetailResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getNotes(),
                customer.getActive(),
                customer.getCreatedAt(),
                customer.getAddresses().stream()
                        .filter(addr -> addr.getActive()) // solo activas
                        .map(CustomerAddressResponse::fromEntity)
                        .toList(),
                stats.orderCount(),
                stats.totalSpent(),
                stats.lastOrderAt());
    }
}
