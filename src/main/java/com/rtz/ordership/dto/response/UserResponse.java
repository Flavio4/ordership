package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Role role,
        Boolean active,
        Instant createdAt) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getActive(),
                user.getCreatedAt());
    }
}
