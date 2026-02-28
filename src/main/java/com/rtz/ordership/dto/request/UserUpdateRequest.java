package com.rtz.ordership.dto.request;

import com.rtz.ordership.entity.enums.Role;

public record UserUpdateRequest(
        String fullName,
        String phone,
        Role role,
        Boolean active) {
}
