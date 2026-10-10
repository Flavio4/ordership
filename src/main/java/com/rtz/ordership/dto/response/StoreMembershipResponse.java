package com.rtz.ordership.dto.response;

import com.rtz.ordership.entity.StoreMember;
import com.rtz.ordership.entity.enums.Role;

import java.util.UUID;

public record StoreMembershipResponse(UUID id, String name, Role role) {

    public static StoreMembershipResponse fromEntity(StoreMember member) {
        return new StoreMembershipResponse(member.getStore().getId(), member.getStore().getName(), member.getRole());
    }
}
