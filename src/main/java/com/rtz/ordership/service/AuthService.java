package com.rtz.ordership.service;

import com.rtz.ordership.dto.request.LoginRequest;
import com.rtz.ordership.dto.request.RefreshTokenRequest;
import com.rtz.ordership.dto.response.LoginResponse;
import com.rtz.ordership.entity.User;
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
    private final UserService userService;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService,
            UserService userService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.userService = userService;
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

        log.info("Login exitoso para: {}", user.getEmail());
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
        String token = jwtProvider.generateToken(user.getEmail());
        String refreshToken = refreshTokenService.issue(user);
        return new LoginResponse(token, refreshToken, userService.getProfile(user));
    }
}
