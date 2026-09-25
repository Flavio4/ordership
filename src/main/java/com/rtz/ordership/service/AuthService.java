package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.LoginRequest;
import com.rtz.ordership.dto.request.RefreshTokenRequest;
import com.rtz.ordership.dto.request.RegisterRequest;
import com.rtz.ordership.dto.response.LoginResponse;
import com.rtz.ordership.dto.response.UserResponse;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.DuplicateResourceException;
import com.rtz.ordership.exception.UnauthorizedException;
import com.rtz.ordership.repository.UserRepository;
import com.rtz.ordership.security.JwtProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Intento de login para email: {}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login fallido - email no encontrado: {}", request.email());
                    return new UnauthorizedException("Credenciales inválidas");
                });

        if (!user.getActive()) {
            log.warn("Login fallido - cuenta desactivada: {}", request.email());
            throw new UnauthorizedException("La cuenta está desactivada");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Login fallido - contraseña incorrecta para: {}", request.email());
            throw new UnauthorizedException("Credenciales inválidas");
        }

        log.info("Login exitoso para: {} [rol: {}]", user.getEmail(), user.getRole());
        return issueSession(user);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse refresh(RefreshTokenRequest request) {
        User user = refreshTokenService.consume(request.refreshToken());
        log.info("Sesión renovada para: {}", user.getEmail());
        return issueSession(user);
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private LoginResponse issueSession(User user) {
        String token = jwtProvider.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.issue(user);
        return new LoginResponse(token, refreshToken, UserResponse.fromEntity(user));
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registro de nuevo usuario - email: {}, rol: {}", request.email(), request.role());

        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registro fallido - email duplicado: {}", request.email());
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
        log.info("Usuario registrado exitosamente - id: {}, email: {}", user.getId(), user.getEmail());
        return UserResponse.fromEntity(user);
    }
}
