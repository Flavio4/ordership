package com.rtz.ordership.service;

import com.rtz.ordership.dto.response.UserResponse;
import com.rtz.ordership.entity.StoreMember;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.entity.enums.Role;
import com.rtz.ordership.repository.StoreMemberRepository;
import com.rtz.ordership.tenant.StoreContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final StoreMemberRepository storeMemberRepository;

    public UserService(StoreMemberRepository storeMemberRepository) {
        this.storeMemberRepository = storeMemberRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(User user) {
        List<StoreMember> memberships = storeMemberRepository.findActiveByUserId(user.getId());
        Role role = StoreContext.current()
                .map(StoreContext.CurrentStore::role)
                .orElse(memberships.size() == 1 ? memberships.getFirst().getRole() : null);
        return UserResponse.of(user, role, memberships);
    }
}
