package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.UserUpdateRequest;
import com.rtz.ordership.dto.response.UserResponse;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    public UserResponse getUserById(UUID id) {
        User user = findUserOrThrow(id);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        User user = findUserOrThrow(id);

        if (request.fullName() != null)
            user.setFullName(request.fullName());
        if (request.phone() != null)
            user.setPhone(request.phone());
        if (request.role() != null)
            user.setRole(request.role());
        if (request.active() != null)
            user.setActive(request.active());

        user = userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = findUserOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }
}
