package com.rtz.ordership.service;

import com.rtz.ordership.entity.RefreshToken;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.UnauthorizedException;
import com.rtz.ordership.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration validity;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-days}") long validityDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.validity = Duration.ofDays(validityDays);
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plus(validity))
                .build());
        return rawToken;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User consume(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido"));
        User user = token.getUser();
        Instant now = Instant.now();

        if (token.getRevokedAt() != null) {
            int revoked = refreshTokenRepository.revokeAllActiveByUser(user, now);
            log.warn("Reuso de refresh token revocado para: {} - se cerraron {} sesiones", user.getEmail(), revoked);
            throw new UnauthorizedException("Refresh token inválido");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new UnauthorizedException("La sesión expiró, iniciá sesión de nuevo");
        }

        token.setRevokedAt(now);
        refreshTokenRepository.save(token);

        if (!user.getActive()) {
            log.warn("Refresh rechazado - cuenta desactivada: {}", user.getEmail());
            throw new UnauthorizedException("La cuenta está desactivada");
        }
        return user;
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
