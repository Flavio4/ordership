package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.StoreMember;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code role} es el rol en la tienda actual (o en la única, si tiene una sola); null si tiene varias y todavía no
 * eligió. {@code stores} son las tiendas activas a las que pertenece.
 */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Role role,
        Boolean active,
        Instant createdAt,
        List<StoreMembershipResponse> stores) {

    public static UserResponse of(User user, Role role, List<StoreMember> memberships) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                role,
                user.getActive(),
                user.getCreatedAt(),
                memberships.stream().map(StoreMembershipResponse::fromEntity).toList());
    }
}
