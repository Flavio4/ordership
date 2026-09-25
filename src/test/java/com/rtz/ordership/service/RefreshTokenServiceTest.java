package com.rtz.ordership.service;

import com.rtz.ordership.entity.RefreshToken;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.exception.UnauthorizedException;
import com.rtz.ordership.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new RefreshTokenService(repository, 30);
        user = User.builder().email("op@ordership.com").active(true).build();
    }

    @Test
    void issueStoresOnlyTheHashWithTheConfiguredValidity() {
        String rawToken = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(Instant.now().plus(Duration.ofDays(30)).minusSeconds(5), Instant.now().plus(Duration.ofDays(30)));
    }

    @Test
    void issuedTokensAreDifferentEachTime() {
        assertThat(service.issue(user)).isNotEqualTo(service.issue(user));
    }

    @Test
    void consumeRevokesTheTokenAndReturnsItsUser() {
        RefreshToken token = storedToken(Instant.now().plus(Duration.ofDays(1)), null);

        User result = service.consume("raw");

        assertThat(result).isSameAs(user);
        assertThat(token.getRevokedAt()).isNotNull();
        verify(repository, never()).revokeAllActiveByUser(any(), any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        storedToken(Instant.now().minusSeconds(1), null);

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void reusingARevokedTokenClosesAllSessionsOfTheUser() {
        storedToken(Instant.now().plus(Duration.ofDays(1)), Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeAllActiveByUser(eq(user), any());
    }

    @Test
    void deactivatedUserCannotRefreshAndTheTokenIsSpent() {
        user.setActive(false);
        RefreshToken token = storedToken(Instant.now().plus(Duration.ofDays(1)), null);

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(UnauthorizedException.class);
        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeIsIdempotent() {
        Instant revokedAt = Instant.now().minusSeconds(60);
        RefreshToken token = storedToken(Instant.now().plus(Duration.ofDays(1)), revokedAt);

        service.revoke("raw");

        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
        verify(repository, never()).save(any());
    }

    private RefreshToken storedToken(Instant expiresAt, Instant revokedAt) {
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("hash")
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        return token;
    }
}
