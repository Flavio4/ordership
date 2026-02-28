package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.Customer;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        String notes,
        Boolean active,
        Instant createdAt) {
    public static CustomerResponse fromEntity(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getNotes(),
                customer.getActive(),
                customer.getCreatedAt());
    }
}
