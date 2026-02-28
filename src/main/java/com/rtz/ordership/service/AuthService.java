package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.LoginRequest;
import com.rtz.ordership.dto.request.RegisterRequest;
import com.rtz.ordership.dto.response.LoginResponse;
import com.rtz.ordership.dto.response.UserResponse;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.DuplicateResourceException;
import com.rtz.ordership.exception.UnauthorizedException;
import com.rtz.ordership.repository.UserRepository;
import com.rtz.ordership.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Credenciales inválidas"));

        if (!user.getActive()) {
            throw new UnauthorizedException("La cuenta está desactivada");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Credenciales inválidas");
        }

        String token = jwtProvider.generateToken(user.getEmail(), user.getRole().name());
        return new LoginResponse(token, UserResponse.fromEntity(user));
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Ya existe un usuario con el email: " + request.email());
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .role(request.role())
                .active(true)
                .build();

        user = userRepository.save(user);
        return UserResponse.fromEntity(user);
    }
}
