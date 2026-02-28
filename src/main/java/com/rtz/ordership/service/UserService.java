package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.UserUpdateRequest;
import com.rtz.ordership.dto.response.UserResponse;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.ResourceNotFoundException;
import com.rtz.ordership.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> getAllUsers() {
        log.info("Listando todos los usuarios");
        List<UserResponse> users = userRepository.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
        log.info("Se encontraron {} usuarios", users.size());
        return users;
    }

    public UserResponse getUserById(UUID id) {
        log.info("Buscando usuario por ID: {}", id);
        User user = findUserOrThrow(id);
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        log.info("Actualizando usuario ID: {}", id);
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
        log.info("Usuario actualizado exitosamente - id: {}, email: {}", user.getId(), user.getEmail());
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public void deactivateUser(UUID id) {
        log.info("Desactivando usuario ID: {}", id);
        User user = findUserOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
        log.info("Usuario desactivado - id: {}, email: {}", user.getId(), user.getEmail());
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado con ID: {}", id);
                    return new ResourceNotFoundException("Usuario no encontrado con ID: " + id);
                });
    }
}
